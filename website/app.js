// Checks Ol Chiki text entirely in the browser. Nothing is sent to a server.
(function () {
  "use strict";
  var OL_START = 0x1C50, OL_END = 0x1C7F;
  var input = document.getElementById("text");
  var result = document.getElementById("result");
  if (!input || !result) return;

  function analyse(text) {
    var ol = 0, other = 0;
    for (var ch of text) {
      var cp = ch.codePointAt(0);
      if (cp >= OL_START && cp <= OL_END) { ol++; }
      else if (/\p{L}/u.test(ch)) { other++; }
    }
    return { ol: ol, other: other };
  }

  input.addEventListener("input", function () {
    var text = input.value;
    if (!text.trim()) { result.textContent = ""; return; }
    var a = analyse(text);
    if (a.ol === 0) {
      result.textContent = "No Ol Chiki letters found. Santali Speaks needs Ol Chiki text.";
    } else if (a.other > 0) {
      result.textContent = "Ol Chiki letters: " + a.ol + ". Other letters: " + a.other + ". Mixed text may not be read well.";
    } else {
      result.textContent = "Ol Chiki letters: " + a.ol + ". Looks good.";
    }
  });
}());
