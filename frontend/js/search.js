const Search = (() => {
  const PAGE_SIZE = 50;
  const MAX_MARKERS = 2000;
  const DEBOUNCE_MS = 300;

  let allResults = [];
  let lastResults = [];
  let debounceTimer = null;
  let lastRadiusLabel = '';
  let lastTotal = 0;

  function init() {
    document.getElementById('btn-search').addEventListener('click', doSearch);
    document.getElementById('radius-select').addEventListener('change', doSearch);
    document.getElementById('keyword-input').addEventListener('input', onKeywordInput);
    document.getElementById('category-filter').addEventListener('change', () => applyFiltersAndSort());
    document.getElementById('sort-select').addEventListener('change', () => applyFiltersAndSort());
  }

  function onKeywordInput() {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      applyFiltersAndSort();
    }, DEBOUNCE_MS);
  }

  async function doSearch() {
    const pos = Geolocation.getPosition();
    const radiusMeters = parseInt(document.getElementById('radius-select').value, 10);
    const radiusKm = radiusMeters / 1000;
    lastRadiusLabel = radiusKm >= 1 ? radiusKm + 'km' : radiusMeters + 'm';

    MapModule.clearMarkers();
    MapModule.clearCircles();
    const circle = MapModule.addCircle(pos.lat, pos.lng, radiusMeters);
    MapModule.fitBoundsToCircle(circle);
    allResults = [];
    lastResults = [];

    document.getElementById('search-info').textContent = '검색 중...';

    const firstResult = await Api.search(pos.lat, pos.lng, radiusKm, 0, PAGE_SIZE);

    if (!firstResult.ok) {
      App.showToast(firstResult.message);
      renderEmptyResults();
      return;
    }

    const total = firstResult.data.total || 0;
    lastTotal = total;
    const firstPage = firstResult.data.businesses || [];

    if (total === 0) {
      renderEmptyResults();
      document.getElementById('search-info').textContent =
        `반경 ${lastRadiusLabel} 내 결과 없음`;
      return;
    }

    allResults = firstPage;

    const loadTotal = Math.min(total, MAX_MARKERS);
    const remainingPages = Math.ceil(loadTotal / PAGE_SIZE) - 1;

    if (remainingPages > 0) {
      const CONCURRENT = 5;
      for (let i = 1; i <= remainingPages; i += CONCURRENT) {
        const batch = [];
        for (let p = i; p < i + CONCURRENT && p <= remainingPages; p++) {
          batch.push(Api.search(pos.lat, pos.lng, radiusKm, p, PAGE_SIZE));
        }
        const results = await Promise.all(batch);
        for (const r of results) {
          if (r.ok && r.data.businesses) {
            allResults = allResults.concat(r.data.businesses);
          }
        }
        document.getElementById('search-info').textContent =
          `반경 ${lastRadiusLabel} 내 ${total.toLocaleString()}개 사업장 (${allResults.length}개 로딩 중...)`;
      }
    }

    extractCategories(allResults);
    applyFiltersAndSort();
  }

  function applyFiltersAndSort() {
    const keyword = document.getElementById('keyword-input').value.trim();
    const category = document.getElementById('category-filter').value;
    const sortBy = document.getElementById('sort-select').value;

    let filtered = allResults;
    filtered = filterByKeyword(filtered, keyword);
    filtered = filterByCategory(filtered, category);
    filtered = sortBusinesses(filtered, sortBy);

    lastResults = filtered;

    MapModule.clearMarkers();
    const pos = Geolocation.getPosition();
    addMarkers(lastResults, pos);
    renderResultList(lastResults);

    const suffix = lastTotal > MAX_MARKERS ? ` (최대 ${MAX_MARKERS.toLocaleString()}개 표시)` : '';
    if (lastResults.length < allResults.length) {
      document.getElementById('search-info').textContent =
        `반경 ${lastRadiusLabel} 내 ${lastTotal.toLocaleString()}개 사업장 (${lastResults.length}개 표시)${suffix}`;
    } else {
      document.getElementById('search-info').textContent =
        `반경 ${lastRadiusLabel} 내 ${lastTotal.toLocaleString()}개 사업장${suffix}`;
    }
  }

  function filterByKeyword(businesses, keyword) {
    if (!keyword) return businesses;
    const lower = keyword.toLowerCase();
    return businesses.filter(biz => biz.name && biz.name.toLowerCase().includes(lower));
  }

  function filterByCategory(businesses, category) {
    if (!category) return businesses;
    return businesses.filter(biz => biz.category === category);
  }

  function sortBusinesses(businesses, sortBy) {
    const sorted = [...businesses];
    if (sortBy === 'name') {
      sorted.sort((a, b) => (a.name || '').localeCompare(b.name || '', 'ko'));
    } else {
      sorted.sort((a, b) => (a.distance || 0) - (b.distance || 0));
    }
    return sorted;
  }

  function extractCategories(businesses) {
    const categories = new Set();
    businesses.forEach(biz => {
      if (biz.category) categories.add(biz.category);
    });

    const select = document.getElementById('category-filter');
    const currentValue = select.value;
    select.innerHTML = '<option value="">전체</option>';
    [...categories].sort((a, b) => a.localeCompare(b, 'ko')).forEach(cat => {
      const option = document.createElement('option');
      option.value = cat;
      option.textContent = cat;
      select.appendChild(option);
    });
    select.value = currentValue;
  }

  function addMarkers(businesses, pos) {
    businesses.forEach(biz => {
      const marker = MapModule.addMarker(biz.latitude || pos.lat, biz.longitude || pos.lng, biz);
      const popupContent = `<strong>${escapeHtml(biz.name)}</strong><br>${escapeHtml(biz.address)}<br><small>${App.formatDistance(biz.distance)}</small>`;
      marker.bindPopup(popupContent);
      marker.on('click', () => onMarkerClick(biz));
    });
  }

  function renderResultList(businesses) {
    const ul = document.getElementById('search-results');
    ul.innerHTML = '';

    businesses.forEach(biz => {
      const favIcon = (typeof Favorites !== 'undefined' && Favorites.isFavorite(biz.id))
        ? '<span class="fav-icon">★</span>' : '';
      const li = document.createElement('li');
      li.dataset.id = biz.id;
      li.innerHTML = `
        <div class="result-name">${favIcon}${escapeHtml(biz.name)}</div>
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
    allResults = [];
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
    allResults = allResults.filter(b => b.id !== id);
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
