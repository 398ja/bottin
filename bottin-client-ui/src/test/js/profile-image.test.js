import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/app.js';
import '../../main/resources/static/js/blossom.js';
import ProfileImage from '../../main/resources/static/js/profile-image.js';

const UPLOADED = 'http://blossom.test/uploaded.png';

function fakeFile(type, size) {
  return { type: type, size: size, arrayBuffer: () => Promise.resolve(new Uint8Array([1]).buffer) };
}

// Renders the three elements a bound field needs and returns them.
function renderField() {
  document.body.innerHTML =
    '<img id="pic-preview" class="hidden" src="/img/default-avatar.svg">' +
    '<input type="file" id="pic-input">' +
    '<div class="form-error hidden" id="pic-error"></div>';
  return {
    input: document.getElementById('pic-input'),
    preview: document.getElementById('pic-preview'),
    error: document.getElementById('pic-error'),
  };
}

// Attaches a file to the input and fires the change event the module listens for.
function selectFile(input, file) {
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  input.dispatchEvent(new Event('change'));
}

const signer = () => ({ id: 'a', sig: 'b' });

beforeEach(() => {
  vi.restoreAllMocks();
  // jsdom implements neither of these.
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

describe('ProfileImage.bind', () => {
  // A rejected file never reaches the network and is reported under the control.
  it('shows a field error and sends no request for a non-image', async () => {
    const { input, error } = renderField();
    const upload = vi.spyOn(window.BlossomUpload, 'upload');
    ProfileImage.bind({
      fileInputId: 'pic-input', previewId: 'pic-preview', errorId: 'pic-error',
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => Promise.resolve(signer),
      onUploaded: () => {},
    });

    selectFile(input, fakeFile('application/pdf', 10));
    await Promise.resolve();

    expect(error.textContent).toBe('Choose an image file.');
    expect(error.className).toBe('form-error');
    expect(upload).not.toHaveBeenCalled();
    expect(input.value).toBe('');
  });

  // A successful upload repoints the preview at the stored URL and reports it back.
  it('previews locally, uploads, and hands the stored URL to onUploaded', async () => {
    const { input, preview } = renderField();
    vi.spyOn(window.BlossomUpload, 'upload').mockResolvedValue({ url: UPLOADED });
    const onUploaded = vi.fn();
    ProfileImage.bind({
      fileInputId: 'pic-input', previewId: 'pic-preview', errorId: 'pic-error',
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => Promise.resolve(signer),
      onUploaded: onUploaded,
    });

    selectFile(input, fakeFile('image/png', 10));
    expect(preview.getAttribute('src')).toBe('blob:preview');
    expect(preview.classList.contains('hidden')).toBe(false);

    await vi.waitFor(() => expect(onUploaded).toHaveBeenCalledWith(UPLOADED));
    expect(preview.getAttribute('src')).toBe(UPLOADED);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
  });

  // A failed upload leaves the user with the image they already had.
  it('restores the previous preview when the upload fails', async () => {
    const { input, preview } = renderField();
    vi.spyOn(window.BlossomUpload, 'upload').mockRejectedValue(new Error('Upload rejected: HTTP 413'));
    const onUploaded = vi.fn();
    const toast = vi.spyOn(window.APP, 'showToast').mockImplementation(() => {});
    ProfileImage.bind({
      fileInputId: 'pic-input', previewId: 'pic-preview', errorId: 'pic-error',
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => Promise.resolve(signer),
      onUploaded: onUploaded,
    });

    selectFile(input, fakeFile('image/png', 10));

    await vi.waitFor(() => expect(toast).toHaveBeenCalled());
    expect(preview.getAttribute('src')).toBe('/img/default-avatar.svg');
    expect(onUploaded).not.toHaveBeenCalled();
    expect(toast).toHaveBeenCalledWith('Upload failed: Upload rejected: HTTP 413', 'error');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
    expect(input.value).toBe('');
  });

  // Dismissing the unlock modal is a deliberate no-op, so it raises no toast.
  it('stays silent when the unlock prompt is cancelled', async () => {
    const { input, preview } = renderField();
    const toast = vi.spyOn(window.APP, 'showToast').mockImplementation(() => {});
    ProfileImage.bind({
      fileInputId: 'pic-input', previewId: 'pic-preview', errorId: 'pic-error',
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => {
        // Message text deliberately does not say "cancelled": the production
        // code must key off err.cancelled, not the message, to reach this branch.
        const cancellation = new Error('unlock dismissed');
        cancellation.cancelled = true;
        return Promise.reject(cancellation);
      },
      onUploaded: () => {},
    });

    selectFile(input, fakeFile('image/png', 10));

    await vi.waitFor(() => expect(preview.getAttribute('src')).toBe('/img/default-avatar.svg'));
    expect(toast).not.toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
  });

  // The onboarding signer decodes the nsec synchronously and throws on a
  // malformed one, before ever returning a promise. That throw must still
  // land in the same revoke + restore + toast path as an async rejection.
  it('recovers when resolveSigner throws synchronously', async () => {
    const { input, preview } = renderField();
    const toast = vi.spyOn(window.APP, 'showToast').mockImplementation(() => {});
    const onUploaded = vi.fn();
    ProfileImage.bind({
      fileInputId: 'pic-input', previewId: 'pic-preview', errorId: 'pic-error',
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => { throw new Error('bad nsec'); },
      onUploaded: onUploaded,
    });

    selectFile(input, fakeFile('image/png', 10));

    await vi.waitFor(() => expect(toast).toHaveBeenCalled());
    expect(preview.getAttribute('src')).toBe('/img/default-avatar.svg');
    expect(onUploaded).not.toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
    expect(toast).toHaveBeenCalledWith('Upload failed: bad nsec', 'error');
  });

  // Binding a field the page does not render must not throw.
  it('is a no-op when the file input is absent', () => {
    document.body.innerHTML = '';
    expect(() => ProfileImage.bind({
      fileInputId: 'missing', previewId: 'missing', errorId: null,
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => Promise.resolve(signer),
      onUploaded: () => {},
    })).not.toThrow();
  });
});
