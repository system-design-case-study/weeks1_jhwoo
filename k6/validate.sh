#!/bin/bash
# K6 스크립트 문법 검증 (T-9.3)
# 사용법: ./k6/validate.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ERRORS=0

for script in "$SCRIPT_DIR"/*.js; do
    echo "검증 중: $(basename "$script")"
    if k6 inspect "$script" > /dev/null 2>&1; then
        echo "  -> 성공"
    else
        echo "  -> 실패"
        ERRORS=$((ERRORS + 1))
    fi
done

if [ $ERRORS -eq 0 ]; then
    echo ""
    echo "모든 K6 스크립트 검증 통과"
    exit 0
else
    echo ""
    echo "$ERRORS 개 스크립트 검증 실패"
    exit 1
fi
