var RelayManager = (function() {
    var relays = [];
    var dirty = false;

    function loadRelays() {
        fetch('/api/v1/relays')
            .then(function(r) { return r.json(); })
            .then(function(data) {
                relays = data.relays || [];
                render();
            });
    }

    function render() {
        var readEl = document.getElementById('read-relays');
        var writeEl = document.getElementById('write-relays');
        var publishBtn = document.getElementById('publish-btn');

        var readRelays = relays.filter(function(r) { return r.read; });
        var writeRelays = relays.filter(function(r) { return r.write; });

        readEl.innerHTML = readRelays.length
            ? readRelays.map(function(r) {
                return '<div class="search-result"><span class="badge badge-primary" style="margin-right: 0.5rem;">Read</span><span style="flex:1; font-size:0.875rem;">' + r.url + '</span><button class="btn btn-sm btn-danger" onclick="RelayManager.remove(\'' + r.url + '\')">×</button></div>';
            }).join('')
            : '<div class="empty-state" style="padding: 1rem;"><p style="font-size: 0.875rem;">No read relays configured</p></div>';

        writeEl.innerHTML = writeRelays.length
            ? writeRelays.map(function(r) {
                return '<div class="search-result"><span class="badge badge-success" style="margin-right: 0.5rem;">Write</span><span style="flex:1; font-size:0.875rem;">' + r.url + '</span><button class="btn btn-sm btn-danger" onclick="RelayManager.remove(\'' + r.url + '\')">×</button></div>';
            }).join('')
            : '<div class="empty-state" style="padding: 1rem;"><p style="font-size: 0.875rem;">No write relays configured</p></div>';

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
            });
    }

    return { loadRelays: loadRelays, addRelay: addRelay, remove: removeRelay, publish: publishRelays };
})();

document.addEventListener('DOMContentLoaded', function() {
    if (document.getElementById('read-relays')) {
        RelayManager.loadRelays();
    }
});
