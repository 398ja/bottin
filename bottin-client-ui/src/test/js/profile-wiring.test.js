import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

// Reads the real source on disk rather than importing it, so this test does
// not execute profile.js (which requires a populated identity to run past
// its early returns) and stays honest about what profile.js's text actually
// says, not what a mock DOM lets it get away with. Paths are resolved from
// the working directory (vitest always runs from bottin-client-ui here).
const profileJsPath = path.resolve('src/main/resources/static/js/profile.js');
const templatePath = path.resolve('src/main/resources/templates/profile-edit.html');

const profileJs = readFileSync(profileJsPath, 'utf8');
const template = readFileSync(templatePath, 'utf8');

function extract(pattern) {
  return Array.from(profileJs.matchAll(pattern), (m) => m[1]);
}

// Every id literal profile.js actually wires up: the fileInputId/previewId/errorId
// values passed to the two ProfileImage.bind({...}) calls, plus the button ids
// passed to bindRemove(...). Pulled straight from the source text, not restated.
const wiredIds = [
  ...extract(/fileInputId:\s*'([^']+)'/g),
  ...extract(/previewId:\s*'([^']+)'/g),
  ...extract(/errorId:\s*'([^']+)'/g),
  ...extract(/bindRemove\('([^']+)'/g),
];

describe('profile.js id wiring stays in sync with profile-edit.html', () => {
  // If this is ever 0, the regexes themselves have gone stale (profile.js
  // changed shape) and the coupling test below would pass vacuously.
  it('extracts at least one id from profile.js', () => {
    expect(wiredIds.length).toBeGreaterThan(0);
  });

  it.each(wiredIds)('profile-edit.html defines id="%s"', (id) => {
    expect(template).toContain('id="' + id + '"');
  });
});
