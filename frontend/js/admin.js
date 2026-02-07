const Admin = (() => {
  let mapClickMode = false;
  let editingBizId = null;

  function init() {
    document.getElementById('login-form').addEventListener('submit', handleLogin);
    document.getElementById('signup-form').addEventListener('submit', handleSignup);
    document.getElementById('show-signup').addEventListener('click', e => {
      e.preventDefault();
      toggleAuthForm('signup');
    });
    document.getElementById('show-login').addEventListener('click', e => {
      e.preventDefault();
      toggleAuthForm('login');
    });
    document.getElementById('btn-logout').addEventListener('click', handleLogout);
    document.getElementById('btn-new-business').addEventListener('click', startCreate);
    document.getElementById('business-form').addEventListener('submit', handleFormSubmit);
    document.getElementById('btn-cancel-biz').addEventListener('click', cancelForm);

    MapModule.getMap().on('click', onMapClick);
  }

  function toggleAuthForm(form) {
    document.getElementById('login-form-wrapper').classList.toggle('hidden', form !== 'login');
    document.getElementById('signup-form-wrapper').classList.toggle('hidden', form !== 'signup');
  }

  async function handleLogin(e) {
    e.preventDefault();
    const email = document.getElementById('login-email').value;
    const password = document.getElementById('login-password').value;

    const result = await Api.login(email, password);
    if (result.ok) {
      App.updateAuthUI();
      App.showToast('로그인되었습니다.');
      clearAuthForms();
    } else {
      App.showMessage('admin-message', result.message, 'error');
    }
  }

  async function handleSignup(e) {
    e.preventDefault();
    const name = document.getElementById('signup-name').value;
    const email = document.getElementById('signup-email').value;
    const password = document.getElementById('signup-password').value;

    const result = await Api.signup(email, password, name);
    if (result.ok) {
      App.showMessage('admin-message', '회원가입이 완료되었습니다. 로그인해주세요.', 'success');
      toggleAuthForm('login');
    } else {
      App.showMessage('admin-message', result.message, 'error');
    }
  }

  function handleLogout() {
    Api.logout();
    App.updateAuthUI();
    cancelForm();
    App.showToast('로그아웃되었습니다.');
  }

  function clearAuthForms() {
    document.getElementById('login-form').reset();
    document.getElementById('signup-form').reset();
  }

  function startCreate() {
    editingBizId = null;
    document.getElementById('form-title').textContent = '사업장 등록';
    document.getElementById('btn-submit-biz').textContent = '등록';
    document.getElementById('business-form').reset();
    document.getElementById('biz-id').value = '';
    document.getElementById('business-form-section').classList.remove('hidden');
    setMapClickMode(true);
    App.clearMessage('admin-message');
    App.showMessage('admin-message', '지도에서 사업장 위치를 클릭하세요.', 'info');
  }

  function startEdit(biz) {
    editingBizId = biz.id;
    document.getElementById('form-title').textContent = '사업장 수정';
    document.getElementById('btn-submit-biz').textContent = '수정';
    document.getElementById('biz-id').value = biz.id;
    document.getElementById('biz-name').value = biz.name || '';
    document.getElementById('biz-address').value = biz.address || '';
    document.getElementById('biz-latitude').value = biz.latitude;
    document.getElementById('biz-longitude').value = biz.longitude;
    document.getElementById('biz-phone').value = biz.phone || '';
    document.getElementById('biz-category').value = biz.category || '';

    App.switchView('admin');
    document.getElementById('business-form-section').classList.remove('hidden');
    setMapClickMode(true);

    MapModule.setTempMarker(biz.latitude, biz.longitude);
  }

  function cancelForm() {
    document.getElementById('business-form-section').classList.add('hidden');
    document.getElementById('business-form').reset();
    editingBizId = null;
    setMapClickMode(false);
    MapModule.clearTempMarker();
    App.clearMessage('admin-message');
  }

  async function handleFormSubmit(e) {
    e.preventDefault();

    const data = {
      name: document.getElementById('biz-name').value,
      address: document.getElementById('biz-address').value,
      latitude: parseFloat(document.getElementById('biz-latitude').value),
      longitude: parseFloat(document.getElementById('biz-longitude').value),
      phone: document.getElementById('biz-phone').value || null,
      category: document.getElementById('biz-category').value || null,
    };

    if (isNaN(data.latitude) || isNaN(data.longitude)) {
      App.showMessage('admin-message', '지도에서 위치를 클릭하여 좌표를 선택해주세요.', 'warn');
      return;
    }

    let result;
    if (editingBizId) {
      result = await Api.updateBusiness(editingBizId, data);
    } else {
      result = await Api.createBusiness(data);
    }

    if (result.ok) {
      const action = editingBizId ? '수정' : '등록';
      App.showToast(`사업장이 ${action}되었습니다.`);
      cancelForm();
      Search.doSearch();
    } else {
      App.showMessage('admin-message', result.message, 'error');
    }
  }

  async function confirmDelete(id) {
    if (!confirm('정말 삭제하시겠습니까?')) return;

    const result = await Api.deleteBusiness(id);
    if (result.ok) {
      App.showToast('사업장이 삭제되었습니다.');
      App.backToList();
      Search.doSearch();
    } else {
      App.showMessage('admin-message', result.message, 'error');
    }
  }

  function onMapClick(e) {
    if (!mapClickMode) return;

    const { lat, lng } = e.latlng;
    document.getElementById('biz-latitude').value = lat.toFixed(6);
    document.getElementById('biz-longitude').value = lng.toFixed(6);
    MapModule.setTempMarker(lat, lng);
  }

  function setMapClickMode(enabled) {
    mapClickMode = enabled;
    const map = MapModule.getMap();
    if (map) {
      map.getContainer().style.cursor = enabled ? 'crosshair' : '';
    }
  }

  return {
    init,
    startEdit,
    confirmDelete,
    setMapClickMode,
  };
})();
