const Api = (() => {
  const BASE_URL = '/api';
  const TOKEN_KEY = 'proximity_jwt';
  const OWNER_KEY = 'proximity_owner';

  const ERROR_MESSAGES = {
    INVALID_COORDINATES: '유효하지 않은 좌표입니다.',
    INVALID_RADIUS: '유효하지 않은 검색 반경입니다.',
    BUSINESS_NOT_FOUND: '사업장을 찾을 수 없습니다.',
    DUPLICATE_BUSINESS: '이미 등록된 사업장입니다.',
    OWNER_NOT_FOUND: '사업주를 찾을 수 없습니다.',
    DUPLICATE_EMAIL: '이미 사용 중인 이메일입니다.',
    INVALID_CREDENTIALS: '이메일 또는 비밀번호가 올바르지 않습니다.',
    UNAUTHORIZED: '로그인이 필요합니다.',
    FORBIDDEN: '이 사업장에 대한 권한이 없습니다.',
    RATE_LIMIT_EXCEEDED: '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.',
  };

  const STATUS_MESSAGES = {
    400: '잘못된 요청입니다.',
    401: '로그인이 필요합니다.',
    403: '접근 권한이 없습니다.',
    404: '요청한 리소스를 찾을 수 없습니다.',
    409: '중복된 데이터가 존재합니다.',
    429: '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.',
    500: '서버 오류가 발생했습니다.',
  };

  function getToken() {
    return localStorage.getItem(TOKEN_KEY);
  }

  function setToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
  }

  function removeToken() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(OWNER_KEY);
  }

  function getOwner() {
    const raw = localStorage.getItem(OWNER_KEY);
    return raw ? JSON.parse(raw) : null;
  }

  function setOwner(owner) {
    localStorage.setItem(OWNER_KEY, JSON.stringify(owner));
  }

  function isLoggedIn() {
    return !!getToken();
  }

  function buildHeaders(hasBody) {
    const headers = {};
    if (hasBody) {
      headers['Content-Type'] = 'application/json';
    }
    const token = getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    return headers;
  }

  function resolveErrorMessage(status, body) {
    if (body && body.code && ERROR_MESSAGES[body.code]) {
      return ERROR_MESSAGES[body.code];
    }
    if (body && body.message) {
      return body.message;
    }
    return STATUS_MESSAGES[status] || '알 수 없는 오류가 발생했습니다.';
  }

  async function request(method, path, options = {}) {
    const { body, params } = options;
    let url = `${BASE_URL}${path}`;

    if (params) {
      const searchParams = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          searchParams.append(key, value);
        }
      });
      const qs = searchParams.toString();
      if (qs) url += `?${qs}`;
    }

    const fetchOptions = {
      method,
      headers: buildHeaders(!!body),
    };
    if (body) {
      fetchOptions.body = JSON.stringify(body);
    }

    const response = await fetch(url, fetchOptions);

    if (response.status === 204) {
      return { ok: true, status: 204, data: null };
    }

    let data = null;
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      data = await response.json();
    }

    if (!response.ok) {
      const message = resolveErrorMessage(response.status, data);
      return {
        ok: false,
        status: response.status,
        code: data?.code || null,
        message,
        data,
      };
    }

    return { ok: true, status: response.status, data };
  }

  function get(path, params) {
    return request('GET', path, { params });
  }

  function post(path, body) {
    return request('POST', path, { body });
  }

  function put(path, body) {
    return request('PUT', path, { body });
  }

  function del(path) {
    return request('DELETE', path);
  }

  async function search(latitude, longitude, radius, category, page, size) {
    const params = { latitude, longitude, radius, page, size };
    if (category) params.category = category;
    return get('/search', params);
  }

  async function getCategories() {
    return get('/categories');
  }

  async function getBusinessDetail(id) {
    return get(`/businesses/${id}`);
  }

  async function createBusiness(data) {
    return post('/businesses', data);
  }

  async function updateBusiness(id, data) {
    return put(`/businesses/${id}/update`, data);
  }

  async function deleteBusiness(id) {
    return del(`/businesses/${id}/delete`);
  }

  async function signup(email, password, name) {
    return post('/owners/signup', { email, password, name });
  }

  async function login(email, password) {
    const result = await post('/owners/login', { email, password });
    if (result.ok && result.data) {
      setToken(result.data.token);
      setOwner({ id: result.data.ownerId, name: result.data.name });
    }
    return result;
  }

  function logout() {
    removeToken();
  }

  return {
    search,
    getCategories,
    getBusinessDetail,
    createBusiness,
    updateBusiness,
    deleteBusiness,
    signup,
    login,
    logout,
    isLoggedIn,
    getToken,
    getOwner,
  };
})();
