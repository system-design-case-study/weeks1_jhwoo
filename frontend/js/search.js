const Search = (() => {
  let lastResults = [];

  function init() {
    document.getElementById('btn-search').addEventListener('click', doSearch);
    document.getElementById('radius-select').addEventListener('change', doSearch);
  }

  async function doSearch() {
    const pos = Geolocation.getPosition();
    const radiusMeters = parseInt(document.getElementById('radius-select').value, 10);
    const radiusKm = radiusMeters / 1000;

    const result = await Api.search(pos.lat, pos.lng, radiusKm);

    MapModule.clearMarkers();
    MapModule.clearCircles();

    const circle = MapModule.addCircle(pos.lat, pos.lng, radiusMeters);
    MapModule.fitBoundsToCircle(circle);

    if (!result.ok) {
      App.showToast(result.message);
      renderEmptyResults();
      return;
    }

    const businesses = result.data.businesses || [];
    lastResults = businesses;

    if (businesses.length === 0) {
      renderEmptyResults();
      document.getElementById('search-info').textContent =
        `반경 ${radiusKm >= 1 ? radiusKm + 'km' : radiusMeters + 'm'} 내 결과 없음`;
      return;
    }

    document.getElementById('search-info').textContent =
      `반경 ${radiusKm >= 1 ? radiusKm + 'km' : radiusMeters + 'm'} 내 ${businesses.length}개 사업장`;

    businesses.forEach(biz => {
      const marker = MapModule.addMarker(biz.latitude || pos.lat, biz.longitude || pos.lng, biz);
      const popupContent = `<strong>${escapeHtml(biz.name)}</strong><br>${escapeHtml(biz.address)}<br><small>${App.formatDistance(biz.distance)}</small>`;
      marker.bindPopup(popupContent);
      marker.on('click', () => onMarkerClick(biz));
    });

    renderResultList(businesses);
  }

  function renderResultList(businesses) {
    const ul = document.getElementById('search-results');
    ul.innerHTML = '';

    businesses.forEach(biz => {
      const li = document.createElement('li');
      li.dataset.id = biz.id;
      li.innerHTML = `
        <div class="result-name">${escapeHtml(biz.name)}</div>
        <div class="result-address">${escapeHtml(biz.address)}</div>
        <div class="result-meta">
          <span>${App.formatDistance(biz.distance)}</span>
          ${biz.category ? `<span>${escapeHtml(biz.category)}</span>` : ''}
        </div>
      `;
      li.addEventListener('click', () => onListItemClick(biz));
      ul.appendChild(li);
    });
  }

  function renderEmptyResults() {
    const ul = document.getElementById('search-results');
    ul.innerHTML = `
      <li class="empty-message">
        주변에 등록된 사업장이 없습니다. 반경을 넓혀보세요.
      </li>
    `;
    lastResults = [];
  }

  function onMarkerClick(biz) {
    highlightListItem(biz.id);
    Detail.show(biz.id);
  }

  function onListItemClick(biz) {
    highlightListItem(biz.id);
    Detail.show(biz.id);
  }

  function highlightListItem(id) {
    document.querySelectorAll('#search-results li').forEach(li => {
      li.classList.toggle('selected', li.dataset.id === String(id));
    });
  }

  function removeFromList(id) {
    const li = document.querySelector(`#search-results li[data-id="${id}"]`);
    if (li) li.remove();
    lastResults = lastResults.filter(b => b.id !== id);
  }

  function getLastResults() {
    return lastResults;
  }

  function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  return { init, doSearch, removeFromList, highlightListItem, getLastResults, escapeHtml };
})();
