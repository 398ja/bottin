// What the user is told after acting on a follow or block list.
//
// One home for it, because three surfaces report the same four outcomes and the
// wording had already drifted apart across them: the same refusal was phrased
// "before following", "before blocking" and "first", and a page offering both lists
// said only "your list could not be read" without saying which.
var ListFeedback = (function () {

    // Reports what actually happened rather than a flat success. A publish some relays
    // refused is neither a success nor a failure, and saying so is the difference
    // between a user who knows where they stand and one who finds out later.
    //
    // `unchanged` says nothing: the state asked for already held, which is not an event
    // worth interrupting anyone about.
    function reportOutcome(result, doneLabel) {
        if (result.unchanged) return;
        if (result.published === 0) {
            window.APP.showToast('Publish failed on all relays', 'error');
        } else if (result.published < result.of) {
            window.APP.showToast(doneLabel + ' · published to ' + result.published
                    + ' of ' + result.of + ' relays', 'success');
        } else {
            window.APP.showToast(doneLabel, 'success');
        }
    }

    // Names the list that could not be read, because a page may offer both and
    // "your list" leaves the user guessing which one is in trouble.
    //
    // A cancelled unlock reaches here with no code and says nothing: cancelling is a
    // decision, not a failure.
    function reportRefusal(err, listName, verb) {
        if (!err) return;
        if (err.code === 'unreadable') {
            window.APP.showToast('Could not read your ' + listName + ' list. Not publishing.', 'error');
        } else if (err.code === 'no_write_relays') {
            window.APP.showToast('Add at least one write relay before ' + verb + '.', 'error');
        }
    }

    return { reportOutcome: reportOutcome, reportRefusal: reportRefusal };
})();

window.ListFeedback = ListFeedback;
