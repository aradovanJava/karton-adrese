/* Podloga karte: svijetla karta (Esri) ili ortofoto Državne geodetske uprave (DOF5 2024., WMS).
   Odabir se pamti u pregledniku i vrijedi za sve karte. */
window.KartonPodloga = (function () {
  'use strict';
  var KLJUC = 'karton-podloga';
  var MOGUCNOSTI = [
    { id: 'karta', naziv: 'Karta', opis: 'Svijetla kartografska podloga' },
    { id: 'ortofoto', naziv: 'Ortofoto DGU', opis: 'Zračna snimka Državne geodetske uprave, 2024.' }
  ];

  function procitaj() {
    try { return localStorage.getItem(KLJUC) === 'ortofoto' ? 'ortofoto' : 'karta'; } catch (e) { return 'karta'; }
  }

  function spremi(v) {
    try { localStorage.setItem(KLJUC, v); } catch (e) { /* pohrana nije dostupna */ }
  }

  function sloj(vrsta) {
    if (vrsta === 'ortofoto') {
      return L.tileLayer.wms('https://geoportal.dgu.hr/services/inspire/orthophoto_2024/wms', {
        layers: 'OI.OrthoimageCoverage', format: 'image/jpeg', transparent: false, version: '1.3.0', maxZoom: 19,
        attribution: 'Ortofoto: © <a href="https://data.gov.hr/ckan/dataset/f06de6cc-9688-4b69-84e6-a24851da131d">' +
          'Državna geodetska uprava, DOF5 2024.</a> · Otvorena dozvola'
      });
    }
    return L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
      maxNativeZoom: 16, maxZoom: 19, attribution: 'Podloga: Esri, HERE, Garmin, © OpenStreetMap contributors'
    });
  }

  function dodaj(karta) {
    var trenutni = procitaj();
    var aktivni = null;
    var poruka = null;

    function postavi(vrsta) {
      if (aktivni) { karta.removeLayer(aktivni); }
      aktivni = sloj(vrsta).addTo(karta);
      aktivni.bringToBack();
      aktivni.on('tileerror', function () {
        if (poruka) { return; }
        poruka = L.control({ position: 'bottomleft' });
        poruka.onAdd = function () {
          var d = L.DomUtil.create('div', 'obavijest info mala');
          d.setAttribute('role', 'status');
          d.textContent = 'Podloga karte trenutno nije dostupna – podaci su i dalje prikazani.';
          return d;
        };
        poruka.addTo(karta);
      });
    }
    postavi(trenutni);

    var Prekidac = L.Control.extend({
      options: { position: 'topright' },
      onAdd: function () {
        var div = L.DomUtil.create('div', 'segmentirano podloga-prekidac');
        div.setAttribute('role', 'group');
        div.setAttribute('aria-label', 'Podloga karte');
        MOGUCNOSTI.forEach(function (m) {
          var b = L.DomUtil.create('button', '', div);
          b.type = 'button';
          b.textContent = m.naziv;
          b.title = m.opis;
          b.setAttribute('aria-pressed', String(m.id === trenutni));
          L.DomEvent.on(b, 'click', function (e) {
            L.DomEvent.stop(e);
            if (m.id === trenutni) { return; }
            trenutni = m.id;
            spremi(trenutni);
            postavi(trenutni);
            Array.prototype.forEach.call(div.querySelectorAll('button'), function (x, i) {
              x.setAttribute('aria-pressed', String(MOGUCNOSTI[i].id === trenutni));
            });
          });
        });
        L.DomEvent.disableClickPropagation(div);
        return div;
      }
    });
    new Prekidac().addTo(karta);
  }

  return { dodaj: dodaj };
})();
