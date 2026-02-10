const MapModule = (() => {
  const SEOUL_CENTER = [37.5665, 126.9780];
  const DEFAULT_ZOOM = 14;

  let map = null;
  let markersLayer = null;
  let circleLayer = null;
  let geohashLayer = null;
  let userMarker = null;
  let tempMarker = null;
  let onMapDoubleClick = null;

  function init(elementId) {
    map = L.map(elementId, { doubleClickZoom: false }).setView(SEOUL_CENTER, DEFAULT_ZOOM);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 19,
    }).addTo(map);

    markersLayer = L.markerClusterGroup({
      maxClusterRadius: 40,
      spiderfyOnMaxZoom: true,
      showCoverageOnHover: false,
      zoomToBoundsOnClick: true,
    });
    map.addLayer(markersLayer);

    circleLayer = L.layerGroup().addTo(map);
    geohashLayer = L.layerGroup().addTo(map);

    map.on('dblclick', (e) => {
      L.DomEvent.preventDefault(e);
      if (onMapDoubleClick) onMapDoubleClick(e.latlng);
    });

    return map;
  }

  function getMap() {
    return map;
  }

  function setView(lat, lng, zoom) {
    if (map) {
      map.setView([lat, lng], zoom || DEFAULT_ZOOM);
    }
  }

  function setUserMarker(lat, lng, isFallback, isManual) {
    if (userMarker) {
      map.removeLayer(userMarker);
    }

    const color = isFallback ? '#888' : (isManual ? '#f39c12' : '#3388ff');
    const icon = L.divIcon({
      className: 'user-marker',
      html: `<div style="
        width:16px;height:16px;border-radius:50%;
        background:${color};border:3px solid #fff;
        box-shadow:0 0 6px rgba(0,0,0,0.3);
      "></div>`,
      iconSize: [16, 16],
      iconAnchor: [8, 8],
    });

    const popupText = isFallback ? '기본 위치 (서울 시청)' :
                      (isManual ? '선택한 위치' : '현재 위치');

    userMarker = L.marker([lat, lng], { icon, zIndexOffset: 1000 })
      .addTo(map)
      .bindPopup(popupText);
  }

  function clearMarkers() {
    markersLayer.clearLayers();
  }

  const CATEGORY_COLORS = {
    '카페': '#8B4513', '식당': '#E74C3C', '편의점': '#27AE60', '약국': '#3498DB',
    '병원': '#E91E63', '미용실': '#9B59B6', '세탁소': '#00BCD4', '문구점': '#FF9800',
    '서점': '#795548', '꽃집': '#F06292',
  };
  const DEFAULT_MARKER_COLOR = '#3388ff';

  function createCategoryIcon(category) {
    const color = CATEGORY_COLORS[category] || DEFAULT_MARKER_COLOR;
    return L.divIcon({
      className: 'category-marker',
      html: `<div style="background:${color};width:12px;height:12px;border-radius:50%;border:2px solid #fff;box-shadow:0 1px 3px rgba(0,0,0,0.3);"></div>`,
      iconSize: [16, 16],
      iconAnchor: [8, 8],
    });
  }

  function addMarker(lat, lng, data) {
    const icon = createCategoryIcon(data.category);
    const marker = L.marker([lat, lng], { icon }).addTo(markersLayer);
    marker.businessData = data;
    return marker;
  }

  function clearCircles() {
    circleLayer.clearLayers();
  }

  function addCircle(lat, lng, radius) {
    return L.circle([lat, lng], {
      radius,
      color: '#3388ff',
      fillColor: '#3388ff',
      fillOpacity: 0.08,
      weight: 2,
    }).addTo(circleLayer);
  }

  function fitBoundsToCircle(circle) {
    if (map && circle) {
      map.fitBounds(circle.getBounds(), { padding: [20, 20] });
    }
  }

  function clearGeohash() {
    geohashLayer.clearLayers();
  }

  function addGeohashRect(bounds, label) {
    const rect = L.rectangle(bounds, {
      color: '#9b59b6',
      weight: 1,
      fillOpacity: 0.05,
      dashArray: '4',
    }).addTo(geohashLayer);

    if (label) {
      const center = rect.getBounds().getCenter();
      L.marker(center, {
        icon: L.divIcon({
          className: 'geohash-label',
          html: label,
          iconSize: [60, 14],
          iconAnchor: [30, 7],
        }),
      }).addTo(geohashLayer);
    }

    return rect;
  }

  function isGeohashVisible() {
    return map.hasLayer(geohashLayer);
  }

  function toggleGeohash(visible) {
    if (visible) {
      map.addLayer(geohashLayer);
    } else {
      map.removeLayer(geohashLayer);
    }
  }

  function setTempMarker(lat, lng) {
    if (tempMarker) {
      map.removeLayer(tempMarker);
    }
    const icon = L.divIcon({
      className: 'temp-marker',
      html: `<div style="
        width:20px;height:20px;border-radius:50%;
        background:#e74c3c;border:3px solid #fff;
        box-shadow:0 0 6px rgba(0,0,0,0.3);
        opacity:0.8;
      "></div>`,
      iconSize: [20, 20],
      iconAnchor: [10, 10],
    });
    tempMarker = L.marker([lat, lng], { icon }).addTo(map);
    return tempMarker;
  }

  function clearTempMarker() {
    if (tempMarker) {
      map.removeLayer(tempMarker);
      tempMarker = null;
    }
  }

  function flyTo(lat, lng, zoom) {
    if (map) {
      map.flyTo([lat, lng], zoom || map.getZoom(), { duration: 0.8 });
    }
  }

  function findMarkerById(businessId) {
    let found = null;
    markersLayer.eachLayer(layer => {
      if (layer.businessData && layer.businessData.id === businessId) {
        found = layer;
      }
    });
    return found;
  }

  function openMarkerPopup(marker) {
    if (marker) {
      marker.openPopup();
    }
  }

  function getZoom() {
    return map ? map.getZoom() : DEFAULT_ZOOM;
  }

  function getBounds() {
    return map ? map.getBounds() : null;
  }

  function setMapDoubleClickHandler(handler) {
    onMapDoubleClick = handler;
  }

  return {
    SEOUL_CENTER,
    DEFAULT_ZOOM,
    init,
    getMap,
    setView,
    setUserMarker,
    clearMarkers,
    addMarker,
    clearCircles,
    addCircle,
    fitBoundsToCircle,
    clearGeohash,
    addGeohashRect,
    isGeohashVisible,
    toggleGeohash,
    setTempMarker,
    clearTempMarker,
    flyTo,
    findMarkerById,
    openMarkerPopup,
    getZoom,
    getBounds,
    setMapDoubleClickHandler,
  };
})();
