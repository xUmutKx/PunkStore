(function () {
  var t = document.body ? document.body.innerText : '';
  var o = { title: document.title, len: t.length, games: [], value: '', level: '', played: '', xp: '' };
  var m;
  m = t.match(/Account Value\s*\n?\s*([^\n]+)/i); if (m) o.value = m[1].trim();
  m = t.match(/Level\s*\n?\s*(\d+)/i); if (m) o.level = m[1];
  m = t.match(/([\d.,]+)\s+out of\s+([\d.,]+)\s+games played/i); if (m) o.played = m[1] + ' / ' + m[2];
  m = t.match(/([\d.,]+)\s*\/\s*([\d.,]+)\s*XP to next level/i); if (m) o.xp = m[1] + ' / ' + m[2];
  function appId(el) { var x = (el.getAttribute('href') || '').match(/\/app\/(\d+)/); return x ? x[1] : null; }
  var seen = {};
  // desktop layout: a table with one row per game
  var rows = document.querySelectorAll('tr[data-appid]');
  for (var r = 0; r < rows.length; r++) {
    var id0 = rows[r].getAttribute('data-appid'); if (!id0 || seen[id0]) continue; seen[id0] = 1;
    var a0 = rows[r].querySelector('a[href*="/app/"]');
    var hm = rows[r].textContent.match(/([\d.,]+)\s*(?:hours|hrs|h)\b/i);
    o.games.push({ id: id0, name: a0 ? a0.textContent.trim() : '', h: hm ? hm[1] : '', price: '', pct: '' });
  }
  // mobile layout: one card per game; climb from the link while the parent still holds a single game
  var links = document.querySelectorAll('a[href*="/app/"]');
  for (var i = 0; i < links.length; i++) {
    var id = appId(links[i]); if (!id || seen[id]) continue;
    var box = links[i];
    for (var k = 0; k < 6 && box.parentElement; k++) {
      var p = box.parentElement, ls = p.querySelectorAll('a[href*="/app/"]'), ids = {}, n = 0;
      for (var j = 0; j < ls.length; j++) { var x = appId(ls[j]); if (x && !ids[x]) { ids[x] = 1; n++; } }
      if (n > 1) break; box = p;
    }
    var lines = (box.innerText || '').split('\n').map(function (s) { return s.trim(); }).filter(function (s) { return s; });
    var h = '', price = '', pct = '', name = '';
    for (var q = 0; q < lines.length; q++) {
      var s = lines[q];
      if (/^[\d.,]+\s*h$/i.test(s)) h = s.replace(/h$/i, '').trim();
      else if (/%$/.test(s) && /\d/.test(s)) pct = s;
      else if (/^(free|no price|-)$/i.test(s)) price = price || s;
      else if (/^[^\w\s]{1,3}\s*[\d.,]+$/.test(s) || /^[\d.,]+\s*(TL|[^\w\s])$/.test(s)) price = s;
      else if (!name && !/^[\d.,]+$/.test(s)) name = s;
    }
    if (!h && !pct && !price) continue; // a stray link, not a game card
    var img = box.querySelector('img');
    seen[id] = 1;
    o.games.push({ id: id, name: name || (img && img.alt) || links[i].textContent.trim(), h: h, price: price, pct: pct });
  }
  return JSON.stringify(o);
})()
