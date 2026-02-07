const Detail = (() => {
  const DAY_NAMES = ['월', '화', '수', '목', '금', '토', '일'];

  function init() {
    document.getElementById('btn-back-to-list').addEventListener('click', () => {
      App.backToList();
    });
  }

  async function show(businessId) {
    const container = document.getElementById('detail-content');
    container.innerHTML = '<p style="color:#999;text-align:center;padding:20px;">불러오는 중...</p>';
    App.showDetail();

    const result = await Api.getBusinessDetail(businessId);

    if (!result.ok) {
      if (result.status === 404) {
        Search.removeFromList(businessId);
        App.showToast('해당 사업장이 삭제되었거나 존재하지 않습니다.');
        App.backToList();
        return;
      }
      container.innerHTML = `<p class="message message-error">${result.message}</p>`;
      return;
    }

    render(result.data, container);
  }

  function render(biz, container) {
    const photoHtml = buildPhotoHtml(biz.photos);
    const hoursHtml = buildHoursHtml(biz.businessHours);
    const isOwner = Api.isLoggedIn() && Api.getOwner()?.id === biz.ownerId;

    container.innerHTML = `
      ${photoHtml}
      <div class="detail-name">${Search.escapeHtml(biz.name)}</div>
      <div class="detail-address">${Search.escapeHtml(biz.address)}</div>

      ${biz.phone ? `
        <div class="detail-section">
          <h4>전화번호</h4>
          <p>${Search.escapeHtml(biz.phone)}</p>
        </div>
      ` : ''}

      ${biz.category ? `
        <div class="detail-section">
          <h4>카테고리</h4>
          <p>${Search.escapeHtml(biz.category)}</p>
        </div>
      ` : ''}

      ${hoursHtml}

      ${isOwner ? `
        <div class="admin-detail-actions">
          <button class="btn-edit" data-id="${biz.id}">수정</button>
          <button class="btn-delete" data-id="${biz.id}">삭제</button>
        </div>
      ` : ''}
    `;

    if (isOwner) {
      container.querySelector('.btn-edit').addEventListener('click', () => {
        Admin.startEdit(biz);
      });
      container.querySelector('.btn-delete').addEventListener('click', () => {
        Admin.confirmDelete(biz.id);
      });
    }
  }

  function buildPhotoHtml(photos) {
    if (!photos || photos.length === 0) {
      return `
        <div class="no-photo">
          <img src="assets/no-photo.svg" alt="사진 없음" style="width:48px;height:48px;opacity:0.4;">
          <span style="margin-left:8px;">등록된 사진이 없습니다</span>
        </div>
      `;
    }
    const sorted = [...photos].sort((a, b) => a.displayOrder - b.displayOrder);
    return sorted.map(p =>
      `<img class="detail-photo" src="${Search.escapeHtml(p.photoUrl)}" alt="사업장 사진">`
    ).join('');
  }

  function buildHoursHtml(hours) {
    if (!hours || hours.length === 0) return '';

    const sorted = [...hours].sort((a, b) => a.dayOfWeek - b.dayOfWeek);
    const rows = sorted.map(h => {
      if (h.closed) {
        return `<tr><td>${DAY_NAMES[h.dayOfWeek]}</td><td class="hours-closed">휴무</td></tr>`;
      }
      return `<tr><td>${DAY_NAMES[h.dayOfWeek]}</td><td>${h.openTime} - ${h.closeTime}</td></tr>`;
    }).join('');

    return `
      <div class="detail-section">
        <h4>영업시간</h4>
        <table class="hours-table">${rows}</table>
      </div>
    `;
  }

  return { init, show };
})();
