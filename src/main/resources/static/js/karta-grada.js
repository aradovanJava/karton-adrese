/* Karta grada 15 minuta: ćelije 1 km² obojene po indeksu ili broju stanovnika. */
(function () {
  'use strict';
  var el = document.getElementById('karta-grada');
  if (!el || !window.L) { return; }
  var BOJE_INDEKS = ['#E8F0FA', '#D4E2F4', '#B9D0EC', '#91B5DF', '#6A98D0', '#4479BD', '#1F4C8F'];
  var BOJE_STAN = ['#E8F4ED', '#D0E8D9', '#AAD5BC', '#7DBB99', '#4C9A73', '#2D7955', '#17563A'];
  var nacin = 'indeks';
  var odabrana = null;

  var karta = L.map(el, { scrollWheelZoom: true }).setView([parseFloat(el.dataset.lat), parseFloat(el.dataset.lon)], 11);
  window.KartonPodloga.dodaj(karta);

  function boja(p) {
    if (nacin === 'indeks') { return BOJE_INDEKS[Math.max(0, Math.min(6, p.indeks15))]; }
    if (p.stanovnika == null) { return '#EEF1F5'; }
    return BOJE_STAN[Math.min(6, Math.floor(p.stanovnika / 3000))];
  }

  function stil(f) {
    var oznacena = odabrana && odabrana.feature === f;
    return { color: oznacena ? '#1C2230' : '#FFFFFF', weight: oznacena ? 2.5 : 0.8, fillColor: boja(f.properties), fillOpacity: oznacena ? 0.85 : 0.62 };
  }

  var detalji = document.querySelector('[data-detalji]');
  function prikazi(p) {
    var d = '<h3>Odabrana ćelija</h3>' +
      '<p><span class="indeks">' + p.indeks15 + '/' + p.najvise + '</span> <span class="muted mala">svakodnevnih usluga u 15 min</span></p>' +
      '<p class="mala"><strong>Nedostaje:</strong> ' + (p.nedostaje.length ? p.nedostaje.join(', ') : 'sve usluge su dostupne') + '</p>' +
      '<p class="mala"><strong>Stanovništvo:</strong> ' + (p.stanovnika == null ? 'nema podatka' : p.stanovnika + ' stanovnika') + '</p>' +
      '<p class="mala muted">' + p.celija + '</p>';
    detalji.innerHTML = d;
  }

  var sloj = null;
  fetch('/api/otvoreni-podaci/indeks15.geojson')
    .then(function (r) { return r.json(); })
    .then(function (gj) {
      if (!gj.features.length) {
        detalji.innerHTML = '<p class="obavijest info mala">Indeks još nije izračunat. Pokrenite uvoz podataka (upute u README.md).</p>';
        return;
      }
      sloj = L.geoJSON(gj, {
        style: stil,
        onEachFeature: function (f, l) {
          l.bindTooltip('Indeks ' + f.properties.indeks15 + '/' + f.properties.najvise, { sticky: true });
          l.on('click', function () {
            var prije = odabrana;
            odabrana = l;
            if (prije) { prije.setStyle(stil(prije.feature)); }
            l.setStyle(stil(f));
            prikazi(f.properties);
          });
          l.on('mouseover', function () { l.setStyle({ fillOpacity: 0.8, weight: 2 }); });
          l.on('mouseout', function () { l.setStyle(stil(f)); });
        }
      }).addTo(karta);
      karta.fitBounds(sloj.getBounds(), { padding: [10, 10] });
    })
    .catch(function () {
      detalji.innerHTML = '<p class="obavijest greska mala">Podatke trenutno nije moguće učitati. Pokušajte ponovno.</p>';
    });

  Array.prototype.forEach.call(document.querySelectorAll('[data-nacin]'), function (b) {
    b.addEventListener('click', function () {
      nacin = b.getAttribute('data-nacin');
      Array.prototype.forEach.call(document.querySelectorAll('[data-nacin]'), function (x) {
        x.setAttribute('aria-pressed', String(x === b));
      });
      document.querySelector('[data-legenda-indeks]').hidden = nacin !== 'indeks';
      document.querySelector('[data-legenda-stanovnistvo]').hidden = nacin === 'indeks';
      if (sloj) { sloj.setStyle(stil); }
    });
  });
})();
