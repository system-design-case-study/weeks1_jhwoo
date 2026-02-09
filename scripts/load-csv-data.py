import argparse
import csv
import time

from pyproj import Transformer

SEED_EMAIL = "seed@proximity.dev"
SEED_PASSWORD_HASH = "$2a$10$dummyhashforseeddata000000000000000000000000000"
SEED_NAME = "시드 사업주"

COL_NAME = "사업장명"
COL_STATUS = "영업상태명"
COL_ROAD_ADDR = "도로명주소"
COL_JIBUN_ADDR = "지번주소"
COL_X = "좌표정보(X)"
COL_Y = "좌표정보(Y)"
COL_PHONE = "전화번호"
COL_CATEGORY = "업태구분명"

MAX_NAME = 255
MAX_ADDRESS = 500
MAX_PHONE = 20
MAX_CATEGORY = 100

INSERT_SQL = """
INSERT INTO businesses (owner_id, name, address, latitude, longitude, location, phone, category)
VALUES %s
ON CONFLICT (owner_id, name, latitude, longitude) DO NOTHING
"""

VALUE_TEMPLATE = (
    "(%(owner_id)s, %(name)s, %(address)s, %(lat)s, %(lng)s,"
    " ST_SetSRID(ST_MakePoint(%(lng)s, %(lat)s), 4326)::geography,"
    " %(phone)s, %(category)s)"
)


def parse_args():
    p = argparse.ArgumentParser(description="CSV 음식점 데이터를 proximity DB에 적재합니다.")
    p.add_argument("--file", required=True, help="CSV 파일 경로")
    p.add_argument("--batch-size", type=int, default=5000)
    p.add_argument("--status", default="영업/정상", help="영업상태명 필터 (빈 문자열이면 전체)")
    p.add_argument("--limit", type=int, default=0, help="최대 행 수 (0=무제한)")
    p.add_argument("--db-host", default="localhost")
    p.add_argument("--db-port", type=int, default=5432)
    p.add_argument("--db-name", default="proximity")
    p.add_argument("--db-user", default="proximity")
    p.add_argument("--db-password", default="proximity_password")
    p.add_argument("--dry-run", action="store_true", help="DB 삽입 없이 파싱만 실행")
    p.add_argument("--wait", type=int, default=0, help="DB 테이블 준비 대기 최대 초 (0=대기 안 함)")
    return p.parse_args()


def connect_with_retry(args, max_wait):
    import psycopg2

    deadline = time.time() + max_wait
    interval = 2
    while True:
        try:
            conn = psycopg2.connect(
                host=args.db_host,
                port=args.db_port,
                dbname=args.db_name,
                user=args.db_user,
                password=args.db_password,
            )
            return conn
        except psycopg2.OperationalError:
            if time.time() >= deadline:
                raise
            print(f"  DB 연결 대기 중... (남은 {int(deadline - time.time())}초)")
            time.sleep(interval)


def wait_for_table(conn, table_name, max_wait):
    deadline = time.time() + max_wait
    interval = 2
    while True:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = %s)",
                (table_name,),
            )
            if cur.fetchone()[0]:
                print(f"  테이블 '{table_name}' 확인 완료")
                return
        if time.time() >= deadline:
            raise RuntimeError(f"테이블 '{table_name}'이 {max_wait}초 내에 생성되지 않았습니다")
        remaining = int(deadline - time.time())
        print(f"  테이블 '{table_name}' 대기 중... (남은 {remaining}초)")
        time.sleep(interval)


def ensure_seed_owner(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM owners WHERE email = %s", (SEED_EMAIL,))
        row = cur.fetchone()
        if row:
            return row[0]
        cur.execute(
            "INSERT INTO owners (email, password_hash, name) VALUES (%s, %s, %s) RETURNING id",
            (SEED_EMAIL, SEED_PASSWORD_HASH, SEED_NAME),
        )
        owner_id = cur.fetchone()[0]
        conn.commit()
        return owner_id


def read_rows(filepath, status_filter):
    transformer = Transformer.from_crs("EPSG:5174", "EPSG:4326", always_xy=True)

    with open(filepath, encoding="cp949", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if status_filter and row.get(COL_STATUS, "") != status_filter:
                continue

            x_str = row.get(COL_X, "").strip()
            y_str = row.get(COL_Y, "").strip()
            if not x_str or not y_str:
                continue

            try:
                x, y = float(x_str), float(y_str)
            except ValueError:
                continue

            if x == 0 or y == 0:
                continue

            name = row.get(COL_NAME, "").strip()
            if not name:
                continue

            address = row.get(COL_ROAD_ADDR, "").strip()
            if not address:
                address = row.get(COL_JIBUN_ADDR, "").strip()
            if not address:
                continue

            lng, lat = transformer.transform(x, y)

            if not (-90 <= lat <= 90 and -180 <= lng <= 180):
                continue

            phone = row.get(COL_PHONE, "").strip() or None
            category = row.get(COL_CATEGORY, "").strip() or None

            yield {
                "name": name[:MAX_NAME],
                "address": address[:MAX_ADDRESS],
                "lat": round(lat, 6),
                "lng": round(lng, 6),
                "phone": phone[:MAX_PHONE] if phone else None,
                "category": category[:MAX_CATEGORY] if category else None,
            }


def main():
    args = parse_args()
    start = time.time()

    print(f"CSV 파일: {args.file}")
    print(f"영업상태 필터: {args.status or '(전체)'}")
    print(f"배치 크기: {args.batch_size}")
    print(f"제한: {args.limit or '무제한'}")
    print(f"드라이런: {args.dry_run}")
    print()

    conn = None
    if not args.dry_run:
        if args.wait > 0:
            conn = connect_with_retry(args, args.wait)
            wait_for_table(conn, "businesses", args.wait)
        else:
            import psycopg2

            conn = psycopg2.connect(
                host=args.db_host,
                port=args.db_port,
                dbname=args.db_name,
                user=args.db_user,
                password=args.db_password,
            )
        owner_id = ensure_seed_owner(conn)
        print(f"Owner ID: {owner_id}")
    else:
        owner_id = -1

    total_read = 0
    total_inserted = 0
    batch = []

    for rec in read_rows(args.file, args.status):
        rec["owner_id"] = owner_id
        batch.append(rec)
        total_read += 1

        if args.limit and total_read >= args.limit:
            if not args.dry_run and batch:
                inserted = flush_batch(conn, batch)
                total_inserted += inserted
            batch = []
            break

        if len(batch) >= args.batch_size:
            if not args.dry_run:
                inserted = flush_batch(conn, batch)
                total_inserted += inserted
            elapsed = time.time() - start
            if args.dry_run:
                print(f"\r  파싱: {total_read:,}건 | 경과: {elapsed:.1f}초", end="", flush=True)
            else:
                print(f"\r  처리: {total_read:,}건 | 삽입: {total_inserted:,}건 | 경과: {elapsed:.1f}초", end="", flush=True)
            batch = []

    if batch and not args.dry_run:
        inserted = flush_batch(conn, batch)
        total_inserted += inserted

    elapsed = time.time() - start
    print()
    print()
    print(f"완료: 필터 통과 {total_read:,}건 | DB 삽입 {total_inserted:,}건 | 소요 {elapsed:.1f}초")

    if conn:
        conn.close()


def flush_batch(conn, batch):
    from psycopg2.extras import execute_values

    with conn.cursor() as cur:
        execute_values(cur, INSERT_SQL, batch, template=VALUE_TEMPLATE, page_size=len(batch))
    conn.commit()
    return len(batch)


if __name__ == "__main__":
    main()
