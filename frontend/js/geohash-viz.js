const GeohashViz = (() => {
  const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz';
  let enabled = false;

  const ZOOM_TO_PRECISION = [
    [18, 8],
    [16, 7],
    [14, 6],
    [11, 5],
    [8, 4],
    [5, 3],
    [0, 2],
  ];

  function init() {
    const btn = document.getElementById('btn-geohash-toggle');
    btn.addEventListener('click', toggle);

    MapModule.getMap().on('moveend', () => {
      if (enabled) refresh();
    });
  }

  function toggle() {
    enabled = !enabled;
    const btn = document.getElementById('btn-geohash-toggle');
    btn.classList.toggle('active', enabled);

    if (enabled) {
      refresh();
    } else {
      MapModule.clearGeohash();
    }
  }

  function getPrecision() {
    const zoom = MapModule.getZoom();
    for (const [minZoom, precision] of ZOOM_TO_PRECISION) {
      if (zoom >= minZoom) return precision;
    }
    return 2;
  }

  function refresh() {
    MapModule.clearGeohash();
    const bounds = MapModule.getBounds();
    if (!bounds) return;

    const precision = getPrecision();
    const sw = bounds.getSouthWest();
    const ne = bounds.getNorthEast();

    const swHash = encode(sw.lat, sw.lng, precision);
    const neHash = encode(ne.lat, ne.lng, precision);

    const swBounds = decodeBounds(swHash);
    const neBounds = decodeBounds(neHash);

    const latStep = swBounds.maxLat - swBounds.minLat;
    const lngStep = swBounds.maxLng - swBounds.minLng;

    let lat = swBounds.minLat;
    let count = 0;
    const maxCells = 200;

    while (lat <= neBounds.maxLat && count < maxCells) {
      let lng = swBounds.minLng;
      while (lng <= neBounds.maxLng && count < maxCells) {
        const centerLat = lat + latStep / 2;
        const centerLng = lng + lngStep / 2;
        const hash = encode(centerLat, centerLng, precision);
        const cellBounds = decodeBounds(hash);

        MapModule.addGeohashRect(
          [[cellBounds.minLat, cellBounds.minLng], [cellBounds.maxLat, cellBounds.maxLng]],
          hash
        );

        lng += lngStep;
        count++;
      }
      lat += latStep;
    }
  }

  function encode(lat, lng, precision) {
    let minLat = -90, maxLat = 90;
    let minLng = -180, maxLng = 180;
    let hash = '';
    let bit = 0;
    let ch = 0;
    let isLng = true;

    while (hash.length < precision) {
      if (isLng) {
        const mid = (minLng + maxLng) / 2;
        if (lng >= mid) {
          ch |= (1 << (4 - bit));
          minLng = mid;
        } else {
          maxLng = mid;
        }
      } else {
        const mid = (minLat + maxLat) / 2;
        if (lat >= mid) {
          ch |= (1 << (4 - bit));
          minLat = mid;
        } else {
          maxLat = mid;
        }
      }

      isLng = !isLng;
      bit++;

      if (bit === 5) {
        hash += BASE32[ch];
        bit = 0;
        ch = 0;
      }
    }

    return hash;
  }

  function decodeBounds(hash) {
    let minLat = -90, maxLat = 90;
    let minLng = -180, maxLng = 180;
    let isLng = true;

    for (const c of hash) {
      const idx = BASE32.indexOf(c);
      for (let bit = 4; bit >= 0; bit--) {
        if (isLng) {
          const mid = (minLng + maxLng) / 2;
          if ((idx >> bit) & 1) {
            minLng = mid;
          } else {
            maxLng = mid;
          }
        } else {
          const mid = (minLat + maxLat) / 2;
          if ((idx >> bit) & 1) {
            minLat = mid;
          } else {
            maxLat = mid;
          }
        }
        isLng = !isLng;
      }
    }

    return { minLat, maxLat, minLng, maxLng };
  }

  return { init };
})();
