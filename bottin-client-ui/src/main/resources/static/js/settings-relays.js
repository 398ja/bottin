var RelayEditor = (function () {
    var userId = null;
    var relays = [];

    function el(id) { return document.getElementById(id); }

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
        removeBtn.textContent = '×';
        removeBtn.addEventListener('click', function () { removeRelay(r.url); });
        div.appendChild(removeBtn);

        return div;
    }

    function renderColumn(elId, filtered, badgeClass, badgeLabel, emptyText) {
        var container = el(elId);
        container.innerHTML = '';
        if (filtered.length) {
            filtered.forEach(function (r) {
                container.appendChild(createRelayRow(r, badgeClass, badgeLabel));
            });
        } else {
            var empty = document.createElement('div');
            empty.className = 'empty-state';
            empty.style.padding = '1rem';
            var p = document.createElement('p');
            p.style.fontSize = '0.875rem';
            p.textContent = emptyText;
            empty.appendChild(p);
            container.appendChild(empty);
        }
    }

    function render() {
        renderColumn('read-relays', relays.filter(function (r) { return r.read; }),
            'badge-primary', 'Read', 'No read relays configured');
        renderColumn('write-relays', relays.filter(function (r) { return r.write; }),
            'badge-success', 'Write', 'No write relays configured');
        el('publish-btn').style.display = relays.length ? 'block' : 'none';
    }

    function persist() {
        APP.saveRelays(userId, relays);
        render();
    }

    function addRelay() {
        var url = el('relay-url').value.trim();
        var read = el('relay-read').checked;
        var write = el('relay-write').checked;
        var error = el('relay-url-error');

        if (!url.startsWith('wss://')) {
            error.style.display = 'block';
            return;
        }
        error.style.display = 'none';

        if (!read && !write) {
            APP.showToast('Select read and/or write permission', 'error');
            return;
        }
        if (relays.some(function (r) { return r.url === url; })) {
            APP.showToast('Relay already added', 'error');
            return;
        }

        relays.push({ url: url, read: read, write: write });
        persist();
        el('relay-url').value = '';
        APP.showToast('Relay added', 'success');
    }

    function removeRelay(url) {
        relays = relays.filter(function (r) { return r.url !== url; });
        persist();
        APP.showToast('Relay removed', 'success');
    }

    // Both the advertised list and the relays it is published to include the
    // deployment's system relays. Events land there, so a kind-10002 that left
    // them out would advertise a list its own author's events cannot be found on.
    function publishRelays() {
        return APP.systemRelays().then(function (system) {
            var advertised = relays.concat(system
                .filter(function (url) { return !relays.some(function (r) { return r.url === url; }); })
                .map(function (url) { return { url: url, read: true, write: true }; }));

            var writeRelays = advertised.filter(function (r) { return r.write; })
                .map(function (r) { return r.url; });
            if (!writeRelays.length) {
                APP.showToast('Add at least one write relay before publishing.', 'error');
                return;
            }

            return APP.ensureUnlocked(userId).then(function (hexKey) {
                var unsigned = NostrPublish.buildRelayListEvent(advertised);
                var signed = NostrCrypto.signEvent(unsigned, hexKey);
                return NostrPublish.publish(new NostrTools.SimplePool(), writeRelays, signed);
            }).then(function (results) {
                var accepted = results.filter(function (r) { return r.accepted; }).length;
                if (accepted) {
                    APP.showToast('Published to ' + accepted + ' of ' + results.length + ' relays', 'success');
                } else {
                    APP.showToast('Publish failed on all relays', 'error');
                }
            });
        }).catch(function () { /* unlock cancelled: local list is retained */ });
    }

    // Renders only the relays this user added. The deployment's system relays are
    // applied to every publish but are not the user's to edit, and they were never
    // in this list to begin with, so they cannot appear here.
    function init() {
        userId = APP.getIdentityUserId();
        if (!userId || !el('read-relays')) return;
        relays = APP.loadRelays(userId);
        render();
    }

    return { init: init, addRelay: addRelay, publishRelays: publishRelays };
})();

// Global handles for the template's inline onclick attributes. Assigned onto
// window explicitly so they resolve both as a plain <script> and when a test
// imports this file as a module.
function addRelay() { RelayEditor.addRelay(); }
function publishRelays() { RelayEditor.publishRelays(); }

window.RelayEditor = RelayEditor;
window.addRelay = addRelay;
window.publishRelays = publishRelays;

document.addEventListener('DOMContentLoaded', function () { RelayEditor.init(); });
