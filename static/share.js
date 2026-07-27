(() => {
  "use strict";

  const status = document.querySelector("#shareStatus");

  function downloadFallback(url, filename) {
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.append(link);
    link.click();
    link.remove();
  }

  document.addEventListener("click", async (event) => {
    const button = event.target.closest("[data-share-file]");
    if (!button) return;

    const original = button.textContent;
    button.disabled = true;
    button.textContent = "準備檔案…";
    if (status) status.textContent = "";

    try {
      const response = await fetch(button.dataset.url);
      if (!response.ok) throw new Error("下載檔案失敗。");
      const blob = await response.blob();
      const file = new File(
        [blob],
        button.dataset.name,
        { type: button.dataset.mime || blob.type }
      );
      const shareData = { files: [file], title: button.dataset.name };
      const canShare =
        navigator.share &&
        (!navigator.canShare || navigator.canShare(shareData));
      if (!canShare) {
        downloadFallback(button.dataset.url, button.dataset.name);
        if (status) {
          status.textContent =
            "呢部瀏覽器未支援檔案分享，已改為下載；請由 Files／檔案開啟。";
        }
        return;
      }
      await navigator.share(shareData);
      if (status) status.textContent = "已開啟手機分享選單。";
    } catch (error) {
      if (error?.name !== "AbortError" && status) {
        status.textContent = error?.message || "未能分享檔案。";
      }
    } finally {
      button.disabled = false;
      button.textContent = original;
    }
  });
})();
