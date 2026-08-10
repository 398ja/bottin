import { describe, it, expect, beforeEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import '../../main/resources/static/js/app.js';
import '../../main/resources/static/js/profile-nudge.js';

const fragment = readFileSync(path.resolve('src/main/resources/templates/fragments/profile-nudge.html'), 'utf8');
const stylesheet = readFileSync(path.resolve('src/main/resources/static/css/styles.css'), 'utf8');

const APP = window.APP;
const USER = 'npub1newcomer';

// The banner as fragments/profile-nudge.html lays it out.
function renderPage() {
  document.body.innerHTML =
    '<div id="profile-nudge" class="hidden">' +
    '  <a id="profile-nudge-link" href="/profile/edit">Complete your profile</a>' +
    '  <button type="button" id="profile-nudge-dismiss">Dismiss</button>' +
    '</div>';
}

function banner() {
  return document.getElementById('profile-nudge');
}

function isShown() {
  return !banner().classList.contains('hidden');
}

function signedInAs(identity) {
  APP.getIdentityUserId = () => (identity ? USER : null);
  APP.loadIdentity = () => identity;
}

beforeEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
  renderPage();
  signedInAs({ userId: USER, npub: USER, nip05: 'newcomer@imani.test' });
});

describe('ProfileNudge visibility', () => {
  // The case the banner exists for: registration collects a handle and nothing
  // else, so a user arrives with no display name and no obvious route to add one.
  it('shows for a user who has published no display name', () => {
    window.ProfileNudge.apply('/search');

    expect(isShown()).toBe(true);
  });

  // Someone who has filled their profile in needs no prompting.
  it('stays hidden once a display name has been published', () => {
    signedInAs({ userId: USER, npub: USER, displayName: 'Alice', nip05: 'alice@imani.test' });

    window.ProfileNudge.apply('/search');

    expect(isShown()).toBe(false);
  });

  // Nobody is signed in, so there is no profile to complete.
  it('stays hidden when no identity is stored', () => {
    signedInAs(null);

    window.ProfileNudge.apply('/search');

    expect(isShown()).toBe(false);
  });

  // The onboarding pages render through the same layout, and an identity exists
  // by the time the backup-key screen loads. Without this the banner would
  // appear alongside the one screen the user must not skim past.
  it('stays hidden throughout onboarding', () => {
    window.ProfileNudge.apply('/onboarding/welcome');

    expect(isShown()).toBe(false);
  });
});

describe('ProfileNudge dismissal', () => {
  // Dismissing must mean dismissed, not dismissed until the next page load.
  it('stays hidden on later visits once dismissed', () => {
    window.ProfileNudge.apply('/search');
    document.getElementById('profile-nudge-dismiss').click();

    renderPage();
    window.ProfileNudge.apply('/search');

    expect(isShown()).toBe(false);
  });

  // The dismissal belongs to the user who made it. A browser is shared, and the
  // next person to register in it has a profile of their own to complete.
  it('shows again for a different user in the same browser', () => {
    window.ProfileNudge.apply('/search');
    document.getElementById('profile-nudge-dismiss').click();

    const other = 'npub1someoneelse';
    APP.getIdentityUserId = () => other;
    APP.loadIdentity = () => ({ userId: other, npub: other, nip05: 'other@imani.test' });
    renderPage();
    window.ProfileNudge.apply('/search');

    expect(isShown()).toBe(true);
  });
});

// Toggling the class is only half of hiding something. These read the template
// and the stylesheet rather than the DOM, because jsdom applies no stylesheet:
// the tests above pass whether or not `.hidden` can actually hide this element,
// and the first version of this banner shipped visible on every page for exactly
// that reason.
describe('the banner can actually be hidden by the hidden class', () => {
  // An inline `display` beats any class, so `.hidden` could never hide it.
  it('sets no inline display on the banner', () => {
    const bannerTag = fragment.match(/<div[^>]*id="profile-nudge"[^>]*>/)[0];

    expect(bannerTag).not.toMatch(/style="[^"]*display\s*:/);
  });

  // At equal specificity the later rule wins, so the layout rule must come
  // before `.hidden` in the stylesheet.
  it('declares the layout rule before the hidden rule', () => {
    expect(stylesheet.indexOf('.profile-nudge {')).toBeGreaterThan(-1);
    expect(stylesheet.indexOf('.profile-nudge {')).toBeLessThan(stylesheet.indexOf('.hidden {'));
  });
});

describe('APP.clearAll', () => {
  // Logging out erases the user from this browser. A dismissal left behind would
  // outlive them and suppress the banner for whoever registers next.
  it('removes the dismissal alongside the identity', () => {
    localStorage.setItem(APP.nudgeKey(USER), '1');
    localStorage.setItem(APP.identityKey(USER), '{}');

    APP.clearAll(USER);

    expect(localStorage.getItem(APP.nudgeKey(USER))).toBeNull();
    expect(localStorage.getItem(APP.identityKey(USER))).toBeNull();
  });
});
