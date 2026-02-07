const App = (() => {
  let currentView = 'search';

  function init() {
    MapModule.init('map');
    Geolocation.init();
    Search.init();
    Detail.init();
    Admin.init();
    GeohashViz.init();
    initModeToggle();
    updateAuthUI();
  }

  function initModeToggle() {
    const btnSearch = document.getElementById('btn-search-mode');
    const btnAdmin = document.getElementById('btn-admin-mode');

    btnSearch.addEventListener('click', () => switchView('search'));
    btnAdmin.addEventListener('click', () => switchView('admin'));
  }

  function switchView(view) {
    currentView = view;

    document.querySelectorAll('.mode-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.panel-view').forEach(v => v.classList.remove('active'));

    if (view === 'search') {
      document.getElementById('btn-search-mode').classList.add('active');
      document.getElementById('search-view').classList.add('active');
      Admin.setMapClickMode(false);
    } else if (view === 'admin') {
      document.getElementById('btn-admin-mode').classList.add('active');
      document.getElementById('admin-view').classList.add('active');
      updateAuthUI();
    } else if (view === 'detail') {
      document.getElementById('detail-view').classList.add('active');
    }
  }

  function showDetail() {
    document.querySelectorAll('.panel-view').forEach(v => v.classList.remove('active'));
    document.getElementById('detail-view').classList.add('active');
  }

  function backToList() {
    document.querySelectorAll('.panel-view').forEach(v => v.classList.remove('active'));

    if (currentView === 'admin') {
      document.getElementById('admin-view').classList.add('active');
    } else {
      document.getElementById('search-view').classList.add('active');
    }
  }

  function updateAuthUI() {
    const authForms = document.getElementById('auth-forms');
    const loggedIn = document.getElementById('logged-in-info');
    const formSection = document.getElementById('business-form-section');
    const adminControls = document.getElementById('admin-controls');

    if (Api.isLoggedIn()) {
      authForms.classList.add('hidden');
      loggedIn.classList.remove('hidden');
      adminControls.classList.remove('hidden');

      const owner = Api.getOwner();
      if (owner) {
        document.getElementById('owner-name').textContent = owner.name;
      }
    } else {
      authForms.classList.remove('hidden');
      loggedIn.classList.add('hidden');
      formSection.classList.add('hidden');
      adminControls.classList.add('hidden');
    }
  }

  function showToast(message, duration) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.classList.remove('hidden');
    setTimeout(() => {
      toast.classList.add('hidden');
    }, duration || 3000);
  }

  function showMessage(containerId, text, type) {
    const container = document.getElementById(containerId);
    container.innerHTML = `<div class="message message-${type}">${text}</div>`;
  }

  function clearMessage(containerId) {
    const container = document.getElementById(containerId);
    container.innerHTML = '';
  }

  function formatDistance(meters) {
    if (meters >= 1000) {
      return (meters / 1000).toFixed(1) + ' km';
    }
    return Math.round(meters) + ' m';
  }

  document.addEventListener('DOMContentLoaded', init);

  return {
    switchView,
    showDetail,
    backToList,
    updateAuthUI,
    showToast,
    showMessage,
    clearMessage,
    formatDistance,
  };
})();
