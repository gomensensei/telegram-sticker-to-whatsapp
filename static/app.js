(() => {
  "use strict";

  const $ = (selector) => document.querySelector(selector);
  const $$ = (selector) => [...document.querySelectorAll(selector)];
  const query = new URLSearchParams(window.location.search);
  const keyFromUrl = query.get("key");
  if (keyFromUrl) {
    sessionStorage.setItem("tgwa-app-key", keyFromUrl);
    history.replaceState({}, "", "/");
  }
  const appKey = keyFromUrl || sessionStorage.getItem("tgwa-app-key") || "";

  const form = $("#convertForm");
  const telegramUrl = $("#telegramUrl");
  const botToken = $("#botToken");
  const rememberToken = $("#rememberToken");
  const packTitle = $("#packTitle");
  const packAuthor = $("#packAuthor");
  const convertButton = $("#convertButton");
  const savedTokenNotice = $("#savedTokenNotice");
  const tokenHelp = $("#tokenHelp");
  const formError = $("#formError");
  const statusCard = $("#statusCard");
  const resultCard = $("#resultCard");
  const progressTrack = $("#progressTrack");
  const progressBar = $("#progressBar");
  const progressNumber = $("#progressNumber");
  const statusKicker = $("#statusKicker");
  const statusTitle = $("#statusTitle");
  const statusMessage = $("#statusMessage");
  const jobLog = $("#jobLog");
  const cancelButton = $("#cancelButton");
  const packageList = $("#packageList");
  const resultSummary = $("#resultSummary");
  const shareQr = $("#shareQr");
  const openFolderButton = $("#openFolderButton");
  const copyLinkButton = $("#copyLinkButton");
  const copyLinkLabel = $("#copyLinkLabel");
  const fatalOverlay = $("#fatalOverlay");

  let hasSavedToken = false;
  let currentJobId = null;
  let currentShareUrl = "";
  let pollTimer = null;
  let qrObjectUrl = null;
  let pendingRememberToken = false;
  let pendingEphemeralToken = false;

  async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    headers.set("X-App-Key", appKey);
    if (options.body !== undefined && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    const response = await fetch(path, { ...options, headers });
    const contentType = response.headers.get("Content-Type") || "";
    const payload = contentType.includes("application/json")
      ? await response.json()
      : await response.text();
    if (!response.ok) {
      const message =
        typeof payload === "object" && payload
          ? payload.error
          : "本機工具暫時未能回應。";
      throw new Error(message || "本機工具暫時未能回應。");
    }
    return payload;
  }

  async function loadConfig() {
    if (!appKey) {
      fatalOverlay.classList.remove("hidden");
      return;
    }
    try {
      const config = await api("/api/config");
      hasSavedToken = Boolean(config.has_token);
      packAuthor.value = config.author || "Telegram 轉換";
      updateTokenState();
    } catch {
      fatalOverlay.classList.remove("hidden");
    }
  }

  function updateTokenState() {
    savedTokenNotice.classList.toggle("hidden", !hasSavedToken);
    tokenHelp.classList.toggle("hidden", hasSavedToken);
    botToken.placeholder = hasSavedToken
      ? "已安全保存；留空即可沿用"
      : "123456789:AA...";
  }

  function showError(message) {
    formError.textContent = message;
    formError.classList.remove("hidden");
  }

  function clearError() {
    formError.textContent = "";
    formError.classList.add("hidden");
  }

  function setFormBusy(busy) {
    convertButton.disabled = busy;
    telegramUrl.disabled = busy;
    botToken.disabled = busy;
    packTitle.disabled = busy;
    packAuthor.disabled = busy;
    rememberToken.disabled = busy;
  }

  function resetRunUi() {
    clearTimeout(pollTimer);
    pollTimer = null;
    currentJobId = null;
    currentShareUrl = "";
    pendingRememberToken = false;
    pendingEphemeralToken = false;
    statusCard.classList.add("hidden");
    statusCard.classList.remove("is-error");
    resultCard.classList.add("hidden");
    cancelButton.classList.remove("hidden");
    packageList.replaceChildren();
    if (qrObjectUrl) {
      URL.revokeObjectURL(qrObjectUrl);
      qrObjectUrl = null;
    }
    shareQr.removeAttribute("src");
  }

  function phaseIndex(phase) {
    return ["validate", "download", "convert", "package", "share"].indexOf(phase);
  }

  function updateStepper(phase) {
    const activeIndex = Math.max(0, phaseIndex(phase));
    $$(".stepper > div").forEach((item, index) => {
      item.classList.toggle("done", index < activeIndex);
      item.classList.toggle("active", index === activeIndex);
    });
  }

  function updateProgress(job) {
    const progress = Math.max(0, Math.min(100, Number(job.progress) || 0));
    statusCard.classList.remove("hidden");
    progressBar.style.width = `${progress}%`;
    progressNumber.textContent = `${progress}%`;
    progressTrack.setAttribute("aria-valuenow", String(progress));
    statusMessage.textContent = job.message || "正在處理…";
    updateStepper(job.phase || "validate");
    jobLog.textContent = (job.logs || []).slice(-70).join("\n");
    jobLog.scrollTop = jobLog.scrollHeight;

    const phaseLabels = {
      validate: ["CHECKING", "正在檢查貼圖包…"],
      download: ["DOWNLOADING", "正在下載 Telegram 貼圖…"],
      convert: ["OPTIMIZING", "正在轉成 WhatsApp 規格…"],
      package: ["PACKAGING", "正在封裝貼圖包…"],
      share: ["READY", "轉換完成。"],
    };
    const labels = phaseLabels[job.phase] || phaseLabels.validate;
    statusKicker.textContent = labels[0];
    statusTitle.textContent = labels[1];
  }

  async function startConversion(event) {
    event.preventDefault();
    clearError();
    resetRunUi();

    if (!hasSavedToken && !botToken.value.trim()) {
      showError("第一次使用要先輸入 Telegram Bot Token。");
      botToken.focus();
      return;
    }

    const payload = {
      url: telegramUrl.value.trim(),
      token: botToken.value.trim(),
      remember: rememberToken.checked,
      title: packTitle.value.trim(),
      author: packAuthor.value.trim(),
    };

    setFormBusy(true);
    try {
      const job = await api("/api/convert", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      currentJobId = job.id;
      pendingRememberToken = Boolean(payload.token && payload.remember);
      pendingEphemeralToken = Boolean(payload.token && !payload.remember);
      statusCard.classList.remove("hidden");
      updateProgress(job);
      pollTimer = setTimeout(pollJob, 500);
      statusCard.scrollIntoView({ behavior: "smooth", block: "center" });
    } catch (error) {
      setFormBusy(false);
      showError(error.message);
    }
  }

  async function pollJob() {
    if (!currentJobId) return;
    try {
      const job = await api(`/api/jobs/${currentJobId}`);
      updateProgress(job);
      if (job.pack_info && pendingRememberToken) {
        hasSavedToken = true;
        pendingRememberToken = false;
        botToken.value = "";
        updateTokenState();
      }
      if (job.pack_info && pendingEphemeralToken) {
        pendingEphemeralToken = false;
        botToken.value = "";
      }
      if (job.status === "done") {
        setFormBusy(false);
        cancelButton.classList.add("hidden");
        await renderResult(job);
        return;
      }
      if (job.status === "error" || job.status === "cancelled") {
        setFormBusy(false);
        statusCard.classList.add("is-error");
        statusKicker.textContent = job.status === "cancelled" ? "CANCELLED" : "FAILED";
        statusTitle.textContent =
          job.status === "cancelled" ? "已取消轉換。" : "今次未轉換成功。";
        statusMessage.textContent = job.error || job.message;
        progressBar.style.width = "0%";
        progressNumber.textContent = "—";
        cancelButton.classList.add("hidden");
        return;
      }
      pollTimer = setTimeout(pollJob, 700);
    } catch (error) {
      setFormBusy(false);
      showError(error.message);
    }
  }

  function packageRow(item, index) {
    const row = document.createElement("div");
    row.className = "package-item";

    const number = document.createElement("span");
    number.textContent = String(index + 1).padStart(2, "0");

    const copy = document.createElement("div");
    const name = document.createElement("strong");
    name.textContent = item.filename;
    const meta = document.createElement("small");
    const animated =
      item.animated_count > 0 ? ` · ${item.animated_count} 張動畫` : "";
    meta.textContent = `${item.sticker_count} 張貼圖${animated}`;
    copy.append(name, meta);

    const size = document.createElement("b");
    size.textContent = item.size_label;
    const download = document.createElement("button");
    download.className = "package-download";
    download.type = "button";
    download.textContent = "直接下載";
    download.addEventListener("click", () => {
      downloadPackage(item.filename, download);
    });
    row.append(number, copy, size, download);
    return row;
  }

  async function downloadPackage(filename, button) {
    if (!currentJobId) return;
    const original = button.textContent;
    button.disabled = true;
    button.textContent = "下載中…";
    try {
      const response = await fetch(
        `/api/jobs/${currentJobId}/download/${encodeURIComponent(filename)}`,
        { headers: { "X-App-Key": appKey } }
      );
      if (!response.ok) {
        let message = "下載失敗。";
        try {
          const payload = await response.json();
          message = payload.error || message;
        } catch {
          // Keep the generic download error.
        }
        throw new Error(message);
      }
      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = filename;
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      setTimeout(() => URL.revokeObjectURL(objectUrl), 2000);
      button.textContent = "已下載";
      setTimeout(() => {
        button.textContent = original;
      }, 1500);
    } catch (error) {
      showError(error.message);
      button.textContent = original;
    } finally {
      button.disabled = false;
    }
  }

  async function renderResult(job) {
    currentShareUrl = job.share_url || "";
    resultSummary.textContent = `已製作 ${job.packages.length} 個貼圖包；手機掃碼即可下載。`;
    packageList.replaceChildren(
      ...job.packages.map((item, index) => packageRow(item, index))
    );

    const response = await fetch(`/api/jobs/${job.id}/qr`, {
      headers: { "X-App-Key": appKey },
    });
    if (response.ok) {
      const blob = await response.blob();
      qrObjectUrl = URL.createObjectURL(blob);
      shareQr.src = qrObjectUrl;
    }

    resultCard.classList.remove("hidden");
    resultCard.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  form.addEventListener("submit", startConversion);

  $("#pasteButton").addEventListener("click", async () => {
    try {
      telegramUrl.value = (await navigator.clipboard.readText()).trim();
      telegramUrl.focus();
    } catch {
      telegramUrl.focus();
      telegramUrl.select();
    }
  });

  $("#revealTokenButton").addEventListener("click", (event) => {
    const reveal = botToken.type === "password";
    botToken.type = reveal ? "text" : "password";
    event.currentTarget.textContent = reveal ? "隱藏" : "顯示";
  });

  $("#forgetTokenButton").addEventListener("click", async () => {
    if (!window.confirm("移除已保存嘅 Telegram Bot Token？")) return;
    try {
      await api("/api/forget-token", {
        method: "POST",
        body: "{}",
      });
      hasSavedToken = false;
      updateTokenState();
      botToken.focus();
    } catch (error) {
      showError(error.message);
    }
  });

  cancelButton.addEventListener("click", async () => {
    if (!currentJobId) return;
    try {
      await api(`/api/jobs/${currentJobId}/cancel`, {
        method: "POST",
        body: "{}",
      });
      pollTimer = setTimeout(pollJob, 100);
    } catch (error) {
      showError(error.message);
    }
  });

  openFolderButton.addEventListener("click", async () => {
    if (!currentJobId) return;
    try {
      await api(`/api/jobs/${currentJobId}/open`, {
        method: "POST",
        body: "{}",
      });
    } catch (error) {
      showError(error.message);
    }
  });

  copyLinkButton.addEventListener("click", async () => {
    if (!currentShareUrl) return;
    try {
      await navigator.clipboard.writeText(currentShareUrl);
      copyLinkLabel.textContent = "已複製";
      setTimeout(() => {
        copyLinkLabel.textContent = "複製手機連結";
      }, 1300);
    } catch {
      showError(`請手動複製：${currentShareUrl}`);
    }
  });

  $("#shutdownButton").addEventListener("click", async () => {
    if (!window.confirm("關閉 TG → WA 轉換工具？")) return;
    try {
      await api("/api/shutdown", {
        method: "POST",
        body: "{}",
      });
    } finally {
      const fatalTitle = fatalOverlay.querySelector("h2");
      const fatalCopy = fatalOverlay.querySelector("p");
      fatalTitle.textContent = "轉換工具已關閉";
      fatalCopy.textContent = "要再用時，雙擊「啟動轉換工具.bat」即可。";
      fatalOverlay.classList.remove("hidden");
    }
  });

  loadConfig();
})();
