const Search = (() => {
  const DEBOUNCE_MS = 300;

  const RADIUS_ZOOM_MAP = {
    500: 18,
    1000: 16,
    2000: 15,
    5000: 14,
    20000: 12,
  };

  const CATEGORY_EMOJI = {
    '카페': '☕', '식당': '🍽️', '편의점': '🏪', '약국': '💊',
    '병원': '🏥', '미용실': '💇', '세탁소': '👔', '문구점': '✏️',
    '서점': '📚', '꽃집': '💐',
  };
  const DEFAULT_EMOJI = '📍';
  const PAGE_SIZE = 200;

  let allResults = [];
  let lastResults = [];
  let debounceTimer = null;
  let currentCategory = null;
  let allCategories = [];
  let currentPage = 0;
  let totalCount = 0;
  let isLoading = false;
  let scrollObserver = null;

  function init() {
    document.getElementById('btn-back-to-categories').addEventListener('click', showCategorySection);
    document.getElementById('radius-select').addEventListener('change', onCategoryRadiusChange);
    document.getElementById('radius-select-results').addEventListener('change', onResultsRadiusChange);
    document.getElementById('category-filter').addEventListener('change', onCategoryFilterChange);
    document.getElementById('keyword-input').addEventListener('input', onKeywordInput);

    loadCategories();
  }

  async function loadCategories() {
    const result = await Api.getCategories();
    if (result.ok && result.data) {
      allCategories = result.data;
      renderCategoryCards(allCategories);
    }
  }

  function renderCategoryCards(categories) {
    const container = document.getElementById('category-cards');
    container.innerHTML = '';
    categories.forEach(cat => {
      const card = document.createElement('button');
      card.className = 'category-card';
      card.innerHTML = `
        <span class="category-emoji">${CATEGORY_EMOJI[cat] || DEFAULT_EMOJI}</span>
        <span class="category-label">${cat}</span>
      `;
      card.addEventListener('click', () => searchByCategory(cat));
      container.appendChild(card);
    });
  }

  async function searchByCategory(category) {
    currentCategory = category;
    showResultsSection();
    syncCategoryFilter();
    await doSearch();
  }

  function onCategoryRadiusChange() {
    syncRadiusSelects('radius-select', 'radius-select-results');
  }

  function onResultsRadiusChange() {
    syncRadiusSelects('radius-select-results', 'radius-select');
    doSearch();
  }

  function syncRadiusSelects(sourceId, targetId) {
    document.getElementById(targetId).value = document.getElementById(sourceId).value;
  }

  function onCategoryFilterChange() {
    const selected = document.getElementById('category-filter').value;
    currentCategory = selected || null;
    doSearch();
  }

  function onKeywordInput() {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      applyLocalFilter();
    }, DEBOUNCE_MS);
  }

  function syncCategoryFilter() {
    const select = document.getElementById('category-filter');
    select.innerHTML = '<option value="">전체</option>';
    allCategories.forEach(cat => {
      const option = document.createElement('option');
      option.value = cat;
      option.textContent = cat;
      select.appendChild(option);
    });
    select.value = currentCategory || '';
  }

  async function doSearch() {
    const pos = Geolocation.getPosition();
    const radiusMeters = parseInt(document.getElementById('radius-select-results').value, 10);
    const radiusKm = radiusMeters / 1000;
    const zoomLevel = radiusToZoom(radiusMeters);

    currentPage = 0;
    totalCount = 0;
    isLoading = false;

    MapModule.clearMarkers();
    MapModule.clearCircles();
    document.getElementById('search-info').textContent = '검색 중...';

    MapModule.setView(pos.lat, pos.lng, zoomLevel);
    MapModule.addCircle(pos.lat, pos.lng, radiusMeters);

    const result = await Api.search(pos.lat, pos.lng, radiusKm, currentCategory, 0, PAGE_SIZE);

    if (!result.ok) {
      App.showToast(result.message);
      renderEmptyResults();
      return;
    }

    const businesses = result.data.businesses || [];
    totalCount = result.data.total || businesses.length;

    if (businesses.length === 0) {
      renderEmptyResults();
      document.getElementById('search-info').textContent =
        '주변에 등록된 사업장이 없습니다. 반경을 넓혀보세요.';
      return;
    }

    allResults = businesses;
    applyLocalFilter();
    setupScrollObserver();
  }

  function hasMore() {
    return (currentPage + 1) * PAGE_SIZE < totalCount;
  }

  async function loadMore() {
    if (isLoading || !hasMore()) return;

    isLoading = true;
    currentPage++;

    const pos = Geolocation.getPosition();
    const radiusMeters = parseInt(document.getElementById('radius-select-results').value, 10);
    const radiusKm = radiusMeters / 1000;

    const result = await Api.search(pos.lat, pos.lng, radiusKm, currentCategory, currentPage, PAGE_SIZE);
    isLoading = false;

    if (!result.ok) return;

    const businesses = result.data.businesses || [];
    if (businesses.length === 0) {
      updateSentinelVisibility();
      return;
    }

    allResults = allResults.concat(businesses);

    const keyword = document.getElementById('keyword-input').value.trim();
    if (keyword) {
      const lower = keyword.toLowerCase();
      const filtered = businesses.filter(biz => biz.name && biz.name.toLowerCase().includes(lower));
      lastResults = lastResults.concat(filtered);
      appendResultList(filtered);
    } else {
      lastResults = lastResults.concat(businesses);
      appendResultList(businesses);
    }

    updateSearchInfo();
    updateSentinelVisibility();
  }

  function setupScrollObserver() {
    if (scrollObserver) scrollObserver.disconnect();

    const sentinel = document.getElementById('scroll-sentinel');
    updateSentinelVisibility();

    scrollObserver = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) {
        loadMore();
      }
    }, { root: document.getElementById('search-view') });

    scrollObserver.observe(sentinel);
  }

  function updateSentinelVisibility() {
    const sentinel = document.getElementById('scroll-sentinel');
    if (hasMore()) {
      sentinel.classList.remove('hidden');
    } else {
      sentinel.classList.add('hidden');
    }
  }

  function updateSearchInfo() {
    const catLabel = currentCategory || '전체';
    const displayed = lastResults.length;
    if (displayed < totalCount) {
      document.getElementById('search-info').textContent =
        `${catLabel} ${totalCount.toLocaleString()}건 (${displayed.toLocaleString()}개 표시)`;
    } else {
      document.getElementById('search-info').textContent =
        `${catLabel} ${totalCount.toLocaleString()}건`;
    }
  }

  function radiusToZoom(radiusMeters) {
    return RADIUS_ZOOM_MAP[radiusMeters] || 14;
  }

  function applyLocalFilter() {
    const keyword = document.getElementById('keyword-input').value.trim();

    let filtered = allResults;
    if (keyword) {
      const lower = keyword.toLowerCase();
      filtered = filtered.filter(biz => biz.name && biz.name.toLowerCase().includes(lower));
    }

    lastResults = filtered;

    MapModule.clearMarkers();
    const pos = Geolocation.getPosition();
    const markersToShow = lastResults.slice(0, PAGE_SIZE);
    addMarkers(markersToShow, pos);
    renderResultList(lastResults);
    updateSearchInfo();
  }

  function addMarkers(businesses, pos) {
    businesses.forEach(biz => {
      const marker = MapModule.addMarker(biz.latitude || pos.lat, biz.longitude || pos.lng, biz);
      const emoji = CATEGORY_EMOJI[biz.category] || DEFAULT_EMOJI;
      const popupContent = `<strong>${emoji} ${escapeHtml(biz.name)}</strong><br>${escapeHtml(biz.address)}<br><small>${App.formatDistance(biz.distance)}</small>`;
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
      const emoji = CATEGORY_EMOJI[biz.category] || DEFAULT_EMOJI;
      const li = document.createElement('li');
      li.dataset.id = biz.id;
      li.innerHTML = `
        <div class="result-name">${favIcon}${emoji} ${escapeHtml(biz.name)}</div>
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

  function appendResultList(businesses) {
    const ul = document.getElementById('search-results');
    businesses.forEach(biz => {
      const favIcon = (typeof Favorites !== 'undefined' && Favorites.isFavorite(biz.id))
        ? '<span class="fav-icon">★</span>' : '';
      const emoji = CATEGORY_EMOJI[biz.category] || DEFAULT_EMOJI;
      const li = document.createElement('li');
      li.dataset.id = biz.id;
      li.innerHTML = `
        <div class="result-name">${favIcon}${emoji} ${escapeHtml(biz.name)}</div>
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
    totalCount = 0;
    document.getElementById('scroll-sentinel').classList.add('hidden');
  }

  function showCategorySection() {
    document.getElementById('category-select-section').classList.remove('hidden');
    document.getElementById('search-results-section').classList.add('hidden');
    currentCategory = null;
    MapModule.clearMarkers();
    MapModule.clearCircles();
  }

  function showResultsSection() {
    document.getElementById('category-select-section').classList.add('hidden');
    document.getElementById('search-results-section').classList.remove('hidden');
  }

  function onMarkerClick(biz) {
    highlightListItem(biz.id);
    Detail.show(biz.id);
  }

  function onListItemClick(biz) {
    highlightListItem(biz.id);
    MapModule.flyTo(biz.latitude, biz.longitude);

    setTimeout(() => {
      const marker = MapModule.findMarkerById(biz.id);
      MapModule.openMarkerPopup(marker);
    }, 900);

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
