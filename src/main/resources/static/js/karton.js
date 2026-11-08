/* Karton adrese: karta okolice sa slojevima, graf sezonske procjene PM10 i dijalog prijave pogreške. */
(function () {
  'use strict';
  var izvor = document.getElementById('karton-podaci');
  if (!izvor) { return; }
  var d = JSON.parse(izvor.textContent);

  function zastiti(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  // ---------------------------------------------------------------- karta
  if (window.L && document.getElementById('karta')) {
    var karta = L.map('karta', { scrollWheelZoom: false }).setView([d.lat, d.lon], 15);
    window.KartonPodloga.dodaj(karta);
    var krug = L.circle([d.lat, d.lon], { radius: d.polumjer, color: '#2F63B0', weight: 2.5, fillColor: '#2F63B0', fillOpacity: 0.06 }).addTo(karta);
    L.circleMarker([d.lat, d.lon], { radius: 9, color: '#FFFFFF', weight: 3, fillColor: '#1F4C8F', fillOpacity: 1 })
      .addTo(karta).bindTooltip('Odabrana adresa: ' + zastiti(d.adresa));
    karta.fitBounds(krug.getBounds(), { padding: [20, 20] });

    var slojevi = {};
    var nazivi = {};
    var boje = {};
    d.objekti.forEach(function (o) {
      if (!slojevi[o.kategorija]) {
        slojevi[o.kategorija] = L.layerGroup().addTo(karta);
        nazivi[o.kategorija] = o.kategorijaNaziv;
        boje[o.kategorija] = o.boja;
      }
      L.circleMarker([o.lat, o.lon], { radius: 7, color: '#FFFFFF', weight: 2, fillColor: o.boja, fillOpacity: 1 })
        .bindPopup('<strong>' + zastiti(o.naziv) + '</strong><br>' + zastiti(o.kategorijaNaziv) + '<br>' +
          zastiti(o.udaljenostTekst) + ' (zračna udaljenost)<br><small>Izvor: ' + zastiti(o.izvor) + '</small>')
        .addTo(slojevi[o.kategorija]);
    });

    var popis = document.getElementById('slojevi');
    if (popis) {
      var naslov = document.createElement('strong');
      naslov.textContent = 'Slojevi karte:';
      popis.appendChild(naslov);
      Object.keys(slojevi).forEach(function (k) {
        var oznaka = document.createElement('label');
        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.checked = true;
        cb.addEventListener('change', function () {
          if (cb.checked) { slojevi[k].addTo(karta); } else { karta.removeLayer(slojevi[k]); }
        });
        var tocka = document.createElement('span');
        tocka.className = 'tocka';
        tocka.style.background = boje[k];
        tocka.setAttribute('aria-hidden', 'true');
        oznaka.appendChild(cb);
        oznaka.appendChild(tocka);
        oznaka.appendChild(document.createTextNode(nazivi[k]));
        popis.appendChild(oznaka);
      });
    }
  }

  // ---------------------------------------------------------------- graf PM10
  var platno = document.getElementById('graf-zraka');
  if (platno && window.Chart && d.zrak && d.zrak.length) {
    var mjeseci = ['Sij', 'Velj', 'Ožu', 'Tra', 'Svi', 'Lip', 'Srp', 'Kol', 'Ruj', 'Lis', 'Stu', 'Pro'];
    new Chart(platno, {
      type: 'line',
      data: {
        labels: d.zrak.map(function (m) { return mjeseci[m.mjesec - 1]; }),
        datasets: [
          { label: 'Gornja granica raspona', data: d.zrak.map(function (m) { return m.gornja; }), borderWidth: 0, pointRadius: 0, fill: '+1', backgroundColor: 'rgba(47, 99, 176, 0.15)' },
          { label: 'Donja granica raspona', data: d.zrak.map(function (m) { return m.donja; }), borderWidth: 0, pointRadius: 0, fill: false },
          { label: 'PM10 – procjena', data: d.zrak.map(function (m) { return m.pm10; }), borderColor: '#2F63B0', backgroundColor: '#2F63B0', borderWidth: 2, pointRadius: 2, tension: 0.3, fill: false },
          { label: 'EU granična vrijednost (godišnja) 40 µg/m³', data: d.zrak.map(function () { return 40; }), borderColor: '#9A5B00', borderDash: [6, 4], borderWidth: 1.5, pointRadius: 0, fill: false }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { position: 'bottom', labels: { boxWidth: 12, filter: function (i) { return i.datasetIndex >= 2; } } },
          tooltip: { filter: function (i) { return i.datasetIndex >= 2; } }
        },
        scales: { y: { beginAtZero: true, title: { display: true, text: 'PM10 (µg/m³)' } } }
      }
    });
  }

  // ---------------------------------------------------------------- interesi i potrebe (primjena odmah)
  var osobno = document.querySelector('[data-osobno]');
  if (osobno) {
    Array.prototype.forEach.call(osobno.querySelectorAll('select, input[type="checkbox"]'), function (polje) {
      polje.addEventListener('change', function () { osobno.submit(); });
    });
  }

  // ---------------------------------------------------------------- dijalog prijave pogreške
  var dijalog = document.getElementById('prijava');
  if (dijalog && typeof dijalog.showModal === 'function') {
    var obrazac = dijalog.querySelector('form');
    var opis = dijalog.querySelector('#opis');
    var greska = dijalog.querySelector('#opis-greska');
    var otvorio = null;

    Array.prototype.forEach.call(document.querySelectorAll('[data-prijava]'), function (b) {
      b.addEventListener('click', function () {
        otvorio = b;
        obrazac.elements.objekt.value = b.getAttribute('data-objekt') || '';
        obrazac.elements.izvor.value = b.getAttribute('data-izvor') || '';
        dijalog.querySelector('[data-prikaz-objekt]').textContent = b.getAttribute('data-objekt') || '—';
        dijalog.querySelector('[data-prikaz-izvor]').textContent = b.getAttribute('data-izvor') || '—';
        greska.hidden = true;
        opis.removeAttribute('aria-invalid');
        dijalog.showModal();
        opis.focus();
      });
    });
    Array.prototype.forEach.call(dijalog.querySelectorAll('[data-zatvori]'), function (b) {
      b.addEventListener('click', function () { dijalog.close(); });
    });
    dijalog.addEventListener('close', function () { if (otvorio) { otvorio.focus(); } });
    obrazac.addEventListener('submit', function (e) {
      if (opis.value.trim().length < 5) {
        e.preventDefault();
        greska.hidden = false;
        opis.setAttribute('aria-invalid', 'true');
        opis.focus();
      }
    });
  }
})();
