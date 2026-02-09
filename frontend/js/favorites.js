const Favorites = (() => {
  const FAVORITES_KEY = 'proximity_favorites';
  const RECENT_KEY = 'proximity_recent';
  const MAX_RECENT = 20;

  let currentTab = 'fav';

  function init() {
    document.querySelectorAll('.fav-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        currentTab = tab.dataset.tab;
        document.querySelectorAll('.fav-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        renderFavoritesView();
      });
    });
  }

  function getFavorites() {
    const raw = localStorage.getItem(FAVORITES_KEY);
    return raw ? JSON.parse(raw) : [];
  }

  function saveFavorites(favorites) {
    localStorage.setItem(FAVORITES_KEY, JSON.stringify(favorites));
  }

  function isFavorite(businessId) {
    return getFavorites().some(f => f.id === businessId);
  }

  function toggleFavorite(biz) {
    const favorites = getFavorites();
    const index = favorites.findIndex(f => f.id === biz.id);

    if (index >= 0) {
      favorites.splice(index, 1);
      saveFavorites(favorites);
      return false;
    }

    favorites.unshift({
      id: biz.id,
      name: biz.name,
      address: biz.address,
      category: biz.category || '',
      savedAt: Date.now(),
    });
    saveFavorites(favorites);
    return true;
  }

  function removeFavorite(businessId) {
    const favorites = getFavorites().filter(f => f.id !== businessId);
    saveFavorites(favorites);
  }

  function getRecent() {
    const raw = localStorage.getItem(RECENT_KEY);
    return raw ? JSON.parse(raw) : [];
  }

  function saveRecent(recent) {
    localStorage.setItem(RECENT_KEY, JSON.stringify(recent));
  }

  function addRecent(biz) {
    let recent = getRecent().filter(r => r.id !== biz.id);
    recent.unshift({
      id: biz.id,
      name: biz.name,
      address: biz.address,
      category: biz.category || '',
      viewedAt: Date.now(),
    });
    if (recent.length > MAX_RECENT) {
      recent = recent.slice(0, MAX_RECENT);
    }
    saveRecent(recent);
  }

  function renderFavoritesView() {
    const container = document.getElementById('favorites-content');

    if (currentTab === 'fav') {
      renderFavoritesList(container);
    } else {
      renderRecentList(container);
    }
  }

  function renderFavoritesList(container) {
    const favorites = getFavorites();

    if (favorites.length === 0) {
      container.innerHTML = '<div class="favorites-empty">저장된 즐겨찾기가 없습니다.</div>';
      return;
    }

    const ul = document.createElement('ul');
    ul.className = 'favorites-list';

    favorites.forEach(fav => {
      const li = document.createElement('li');
      li.innerHTML = `
        <div>
          <div class="result-name">${Search.escapeHtml(fav.name)}</div>
          <div class="result-address">${Search.escapeHtml(fav.address)}</div>
        </div>
        <button class="fav-remove-btn" title="삭제">&times;</button>
      `;
      li.querySelector('.result-name').addEventListener('click', () => Detail.show(fav.id));
      li.querySelector('.fav-remove-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        removeFavorite(fav.id);
        renderFavoritesView();
      });
      ul.appendChild(li);
    });

    container.innerHTML = '';
    container.appendChild(ul);
  }

  function renderRecentList(container) {
    const recent = getRecent();

    if (recent.length === 0) {
      container.innerHTML = '<div class="favorites-empty">최근 본 사업장이 없습니다.</div>';
      return;
    }

    const ul = document.createElement('ul');
    ul.className = 'favorites-list';

    recent.forEach(item => {
      const li = document.createElement('li');
      li.innerHTML = `
        <div>
          <div class="result-name">${Search.escapeHtml(item.name)}</div>
          <div class="result-address">${Search.escapeHtml(item.address)}</div>
        </div>
      `;
      li.addEventListener('click', () => Detail.show(item.id));
      ul.appendChild(li);
    });

    container.innerHTML = '';
    container.appendChild(ul);
  }

  return { init, isFavorite, toggleFavorite, addRecent, renderFavoritesView };
})();
