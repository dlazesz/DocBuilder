function sel(s, dom) {
	return (dom || document).querySelector(s) || {};
}
function each(sel, fn, dom) {
	var arr = typeof (sel) == 'string' ? ((dom || document).querySelectorAll(sel) || []) : (
		sel instanceof HTMLElement ? [sel] : sel
	)
	for (var i = 0; i < arr.length; ++i) if (fn(arr[i], i) === false) break;
}
function evt(sel, t, fn, dom) {
	each(sel, function (i) {
		t.split(' ').forEach(function (e) { i.addEventListener(e, fn); });
	}, dom);
}

document.addEventListener('click', function (e) {
	if (e.target && e.target.matches('a[href="#"]')) {
		e.preventDefault();
	}
});

window.addEventListener('DOMContentLoaded', function () {
	var _instanceId = Math.floor(Math.random() * 900000) + 100000;
	navigator.sendBeacon('/add-session?id=' + _instanceId)
	window.addEventListener('unload', function () {
		navigator.sendBeacon('/del-session?id=' + _instanceId);
	});
});

function ttip(dom) {
	var c = dom.offsetParent;
	var t = document.createElement('div');
	t.className = 'tooltip';
	if (dom.offsetTop > c.clientHeight / 2) {
		t.style.bottom = (c.clientHeight - dom.offsetTop) + 'px';
	} else {
		t.style.top = (dom.offsetTop + dom.offsetHeight) + 'px';
	}
	if (dom.offsetLeft < c.clientWidth / 2) {
		t.style.left = dom.offsetLeft + 'px';
	} else {
		t.style.right = (c.clientWidth - dom.offsetLeft - dom.offsetWidth) + 'px';
	}
	each(c.children, function (i) {
		if (i.matches('.tooltip')) {
			i.remove();
		}
	})
	c.appendChild(t);
	return t;
}

var Editor = function (dom) {
	this.dom = dom;
	this.data = {};

	var self = this;

	dom.addEventListener('change', function (e) {
		if (e.target && e.target.matches('[data-cid]')) {
			self.onchange(e.target);
		}
	});

	dom.addEventListener('click', function (e) {
		if (e.target && e.target.matches('[data-render]')) {
			//TODO: save before?
			dom.innerHTML = '';
			var cid = parseInt(e.target.dataset.render);
			if (self.chunks.length) self.render(cid);
		}

		if (e.target && e.target.matches('.plus-one')) {
			var cid = parseInt(e.target.dataset.cid);
			dom.insertBefore(self.renderChunk(cid), e.target);
			if (self.chunks.length > cid + 1) {
				e.target.dataset.cid = cid + 1;
			} else {
				e.target.remove();
			}
		}
	});
}
Editor.TYPES = {
	_default_: {
		onchange: function (input, chunk) {
			return input.value;
		},
		render: function (chunk) {
			if (!chunk.id || !chunk.name) {
				var e = document.createElement('pre');
				e.textContent = chunk.value;
				return e;
			}
			var e = document.createElement('textarea');
			e.className = 'input';
			e.textContent = chunk.value;
			return e;
		}
	}
};
Editor.prototype.load = function (data, show_filler) {
	if (!data.id) return;
	this.id = localStorage.last_id = data.id;

	if (!show_filler) {
		this.chunks = [];
		for (var c in (data.chunks || [])) {
			if (!data.chunks[c].name) continue;
			this.chunks.push(data.chunks[c]);
		}
	} else {
		this.chunks = data.chunks || [];
	}

	this.dom.innerHTML = '';
	if (this.chunks.length) this.render(0);

	addMsg('Document loaded', 'success');
}
Editor.prototype.onchange = function (input) {
	var c = this.chunks[input.dataset.cid];
	var t = c.name ? Editor.TYPES[c.name] : false;
	if (c.id) {
		c.value = (t || Editor.TYPES._default_).onchange(input, c);
		save([c]);
	} else if (t) {
		t.onchange(input, c);
	}
}
Editor.prototype.render = function (cid) {
	this.dom.appendChild(this.renderPaginator(cid));
	this.dom.appendChild(this.renderChunk(cid));

	if (this.chunks.length > cid + 1) {
		var a = document.createElement('a');
		a.setAttribute('href', '#');
		a.className = 'btn plus-one';
		a.dataset.cid = cid + 1;
		a.innerHTML = '+1';
		this.dom.appendChild(a);
	}
}
Editor.prototype.renderChunk = function (cid) {
	var c = this.chunks[cid];
	var t = c.name ? Editor.TYPES[c.name] : false;
	var e = (t || Editor.TYPES._default_).render(c);
	if (e) e.dataset.cid = cid;
	return e;
}
Editor.prototype.renderPaginator = function (cid) {
	var cur = cid + 1;
	var max = (this.chunks || []).length
	if (max < 1) return;
	var html = '';
	if (cur > 2) {
		html += '<li><a href="#" class="btn" data-render="0">(1) &lt;&lt;</a></li>';
	}
	if (cur > 1) {
		html += '<li><a href="#" class="btn" data-render="' + (cid - 1) + '">&lt;</a></li>';
	}
	if (cur > 4) {
		html += '<li class="disabled"><a>...</a></li>';
	}
	var s = Math.min(max, cur + 3);
	for (var p = Math.max(1, cur - 3); p <= s; ++p) {
		html += '<li class="' + (p == cur ? 'active' : '') + '"><a href="#" class="btn" data-render="' + (p - 1) + '">' + p + '</a></li>';
	}
	if (max > cur + 3) {
		html += '<li class="disabled"><a>...</a></li>';
	}
	if (max > cur) {
		html += '<li><a href="#" class="btn" data-render="' + (cid + 1) + '">&gt;</a></li>';
	}
	if (max > cur + 1) {
		html += '<li><a href="#" class="btn" data-render="' + (max - 1) + '">&gt;&gt; (' + max + ')</a></li>';
	}
	var p = document.createElement('ul');
	p.className = "pagination";
	p.innerHTML = html;
	return p;
}

var editor = new Editor(sel('#editor'));

function addMsg(message, cls) {
	var m = document.createElement('div');
	m.className = cls || 'error';
	m.innerHTML = message;
	sel('#message').appendChild(m);
	setTimeout(function () { m.remove() }, 5000);
}
function delMsg() {
	sel('#message').innerHTML = '';
}

function open() {
	fetch('/open').then(r => r.json()).then(function (data) {
		if (!data.success) {
			addMsg(data.error || 'Unknown Error');
			return;
		}
		editor.load(data);
	});
}

function restore() {
	fetch('/restore?id=' + (localStorage.last_id || 0)).then(r => r.json()).then(function (data) {
		if (!data.success) {
			addMsg(data.error || 'Unknown Error');
			return;
		}
		editor.load(data);
	});
}

function save(chunks) {
	fetch('/save?create=' + (chunks ? 0 : 1) + '&id=' + (editor.id || 0), {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify(chunks || editor.chunks)
	}).then(r => r.json()).then(function (data) {
		if (!data.success) {
			addMsg(data.error || 'Unknown Error');
			return;
		}
		addMsg('Document saved', 'success');
	});
}

// $(window).on('load resize', function () {
// 	$('body').addClass('resizing');

// 	$('body').removeClass('resizing');
// });

