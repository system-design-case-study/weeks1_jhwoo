const Geolocation = (() => {
  const FALLBACK_LAT = 37.5665;
  const FALLBACK_LNG = 126.9780;
  const TIMEOUT_MS = 10000;

  let currentLat = FALLBACK_LAT;
  let currentLng = FALLBACK_LNG;
  let isFallback = true;
  let isManualPosition = false;

  function init() {
    if (!window.isSecureContext) {
      applyFallback('HTTPS가 아닌 환경에서는 위치 정보를 사용할 수 없습니다. 기본 위치(서울 시청)를 사용합니다.');
      return;
    }

    if (!navigator.geolocation) {
      applyFallback('이 브라우저는 위치 정보를 지원하지 않습니다. 기본 위치(서울 시청)를 사용합니다.');
      return;
    }

    navigator.geolocation.getCurrentPosition(onSuccess, onError, {
      enableHighAccuracy: true,
      timeout: TIMEOUT_MS,
      maximumAge: 300000,
    });
  }

  function onSuccess(position) {
    currentLat = position.coords.latitude;
    currentLng = position.coords.longitude;
    isFallback = false;
    isManualPosition = false;

    MapModule.setView(currentLat, currentLng);
    MapModule.setUserMarker(currentLat, currentLng, false, false);
  }

  function onError(error) {
    let message;
    switch (error.code) {
      case error.PERMISSION_DENIED:
        message = '위치 정보 접근이 거부되었습니다. 기본 위치(서울 시청)를 사용합니다.';
        break;
      case error.TIMEOUT:
        message = '위치 정보 요청 시간이 초과되었습니다. 기본 위치(서울 시청)를 사용합니다.';
        break;
      default:
        message = '위치 정보를 가져올 수 없습니다. 기본 위치(서울 시청)를 사용합니다.';
    }
    applyFallback(message);
  }

  function applyFallback(message) {
    currentLat = FALLBACK_LAT;
    currentLng = FALLBACK_LNG;
    isFallback = true;
    isManualPosition = false;

    MapModule.setView(FALLBACK_LAT, FALLBACK_LNG);
    MapModule.setUserMarker(FALLBACK_LAT, FALLBACK_LNG, true, false);
    App.showToast(message, 5000);
  }

  function requestPosition() {
    if (!window.isSecureContext || !navigator.geolocation) {
      App.showToast('위치 정보를 사용할 수 없습니다.');
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        onSuccess(position);
        Search.doSearch();
      },
      onError,
      { enableHighAccuracy: true, timeout: TIMEOUT_MS, maximumAge: 0 }
    );
  }

  function setPosition(lat, lng) {
    currentLat = lat;
    currentLng = lng;
    isFallback = false;
    isManualPosition = true;

    MapModule.setUserMarker(lat, lng, false, true);
  }

  function getPosition() {
    return { lat: currentLat, lng: currentLng, isFallback };
  }

  return { init, getPosition, requestPosition, setPosition };
})();
