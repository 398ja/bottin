var RelayManager = (function() {
    var relays = [];
    var dirty = false;
    var loading = false;

    function loadRelays() {
        if (loading) return;
        loading = true;
        fetch('/api/v1/relays')
            .then(function(r) { return r.json(); })
            .then(function(data) {
                relays = data.relays || [];
                render();
            })
            .catch(function() {
                APP.showToast('Failed to load relays', 'error');
            })
            .finally(function() {
                loading = false;
            });
    }

    function createRelayRow(r, badgeClass, badgeLabel) {
        var div = document.createElement('div');
        div.className = 'search-result';

        var badge = document.createElement('span');
        badge.className = 'badge ' + badgeClass;
        badge.style.marginRight = '0.5rem';
        badge.textContent = badgeLabel;
        div.appendChild(badge);

        var urlSpan = document.createElement('span');
        urlSpan.style.flex = '1';
        urlSpan.style.fontSize = '0.875rem';
        urlSpan.textContent = r.url;
        div.appendChild(urlSpan);

        var removeBtn = document.createElement('button');
        removeBtn.className = 'btn btn-sm btn-danger';
        removeBtn.textContent = '\u00D7';
        removeBtn.addEventListener('click', function() { removeRelay(r.url); });
        div.appendChild(removeBtn);

        return div;
    }

    function render() {
        var readEl = document.getElementById('read-relays');
        var writeEl = document.getElementById('write-relays');
        var publishBtn = document.getElementById('publish-btn');

        var readRelays = relays.filter(function(r) { return r.read; });
        var writeRelays = relays.filter(function(r) { return r.write; });

        readEl.innerHTML = '';
        if (readRelays.length) {
            readRelays.forEach(function(r) {
                readEl.appendChild(createRelayRow(r, 'badge-primary', 'Read'));
            });
        } else {
            var empty = document.createElement('div');
            empty.className = 'empty-state';
            empty.style.padding = '1rem';
            var p = document.createElement('p');
            p.style.fontSize = '0.875rem';
            p.textContent = 'No read relays configured';
            empty.appendChild(p);
            readEl.appendChild(empty);
        }

        writeEl.innerHTML = '';
        if (writeRelays.length) {
            writeRelays.forEach(function(r) {
                writeEl.appendChild(createRelayRow(r, 'badge-success', 'Write'));
            });
        } else {
            var empty = document.createElement('div');
            empty.className = 'empty-state';
            empty.style.padding = '1rem';
            var p = document.createElement('p');
            p.style.fontSize = '0.875rem';
            p.textContent = 'No write relays configured';
            empty.appendChild(p);
            writeEl.appendChild(empty);
        }

        publishBtn.style.display = dirty ? 'block' : 'none';
    }

    function addRelay() {
        var url = document.getElementById('relay-url').value.trim();
        var read = document.getElementById('relay-read').checked;
        var write = document.getElementById('relay-write').checked;
        var error = document.getElementById('relay-url-error');

        if (!url.startsWith('wss://')) {
            error.style.display = 'block';
            return;
        }
        error.style.display = 'none';

        if (!read && !write) {
            APP.showToast('Select read and/or write permission', 'error');
            return;
        }

        fetch('/api/v1/relays', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url: url, read: read, write: write })
        }).then(function(r) {
            if (r.ok) {
                APP.showToast('Relay added', 'success');
                dirty = true;
                loadRelays();
                document.getElementById('relay-url').value = '';
            } else {
                APP.showToast('Failed to add relay', 'error');
            }
        }).catch(function() {
            APP.showToast('Failed to add relay', 'error');
        });
    }

    function removeRelay(url) {
        fetch('/api/v1/relays', {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url: url })
        }).then(function(r) {
            if (r.ok) {
                APP.showToast('Relay removed', 'success');
                dirty = true;
                loadRelays();
            }
        }).catch(function() {
            APP.showToast('Failed to remove relay', 'error');
        });
    }

    function publishRelays() {
        fetch('/api/v1/relays/publish', { method: 'POST' })
            .then(function(r) {
                if (r.ok) {
                    dirty = false;
                    APP.showToast('Published!', 'success');
                    render();
                } else {
                    APP.showToast('Publish failed', 'error');
                }
            }).catch(function() {
                APP.showToast('Publish failed', 'error');
            });
    }

    return { loadRelays: loadRelays, addRelay: addRelay, remove: removeRelay, publish: publishRelays };
})();

document.addEventListener('DOMContentLoaded', function() {
    if (document.getElementById('read-relays')) {
        RelayManager.loadRelays();
    }
});
