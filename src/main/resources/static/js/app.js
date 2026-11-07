/* Zajedničko ponašanje svih stranica: izbornik, prijedlozi adresa, kopiranje, obavijesti. */
(function () {
  'use strict';

  // Izbornik na malim zaslonima
  var gumb = document.querySelector('[data-izbornik]');
  var nav = document.getElementById('glavna-navigacija');
  if (gumb && nav) {
    gumb.addEventListener('click', function () {
      var otvoren = nav.classList.toggle('otvoren');
      gumb.setAttribute('aria-expanded', String(otvoren));
    });
  }

  // Prijedlozi adresa (lokalni registar ulica i poznatih adresa – bez vanjskih poziva)
  Array.prototype.forEach.call(document.querySelectorAll('[data-prijedlozi]'), function (polje) {
    var popis = document.getElementById(polje.getAttribute('list'));
    var odgoda = null;
    polje.addEventListener('input', function () {
      clearTimeout(odgoda);
      var q = polje.value.trim();
      if (q.length < 2 || !popis) { return; }
      odgoda = setTimeout(function () {
        fetch('/api/adrese?q=' + encodeURIComponent(q))
          .then(function (r) { return r.ok ? r.json() : []; })
          .then(function (adrese) {
            popis.innerHTML = '';
            adrese.forEach(function (a) {
              var o = document.createElement('option');
              o.value = a;
              popis.appendChild(o);
            });
          })
          .catch(function () { /* prijedlozi su pomoć, ne preduvjet */ });
      }, 200);
    });
  });

  // Kratka obavijest (toast)
  window.KartonObavijest = function (tekst) {
    var t = document.createElement('div');
    t.className = 'toast';
    t.setAttribute('role', 'status');
    t.textContent = tekst;
    document.body.appendChild(t);
    setTimeout(function () { t.remove(); }, 4000);
  };
  Array.prototype.forEach.call(document.querySelectorAll('[data-toast]'), function (t) {
    setTimeout(function () { t.remove(); }, 5000);
  });

  function kopiraj(tekst) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(tekst).then(function () { window.KartonObavijest('Kopirano.'); });
    } else {
      window.prompt('Kopirajte:', tekst);
    }
  }
  Array.prototype.forEach.call(document.querySelectorAll('[data-kopiraj]'), function (b) {
    b.addEventListener('click', function () { kopiraj(window.location.href); });
  });
  Array.prototype.forEach.call(document.querySelectorAll('[data-kopiraj-polje]'), function (b) {
    b.addEventListener('click', function () {
      var p = document.getElementById(b.getAttribute('data-kopiraj-polje'));
      if (p) { kopiraj(p.value); }
    });
  });
})();
