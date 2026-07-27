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
  const packModeButton = $("#packModeButton");
  const videoModeButton = $("#videoModeButton");
  const formatBadge = $("#formatBadge");
  const videoForm = $("#videoForm");
  const videoFile = $("#videoFile");
  const videoDrop = $("#videoDrop");
  const videoEditor = $("#videoEditor");
  const videoPreview = $("#videoPreview");
  const videoStage = $("#videoStage");
  const videoFileName = $("#videoFileName");
  const videoFileMeta = $("#videoFileMeta");
  const clipStart = $("#clipStart");
  const clipStartValue = $("#clipStartValue");
  const clipDuration = $("#clipDuration");
  const clipDurationValue = $("#clipDurationValue");
  const videoScale = $("#videoScale");
  const videoScaleValue = $("#videoScaleValue");
  const videoOffsetX = $("#videoOffsetX");
  const videoOffsetXValue = $("#videoOffsetXValue");
  const videoOffsetY = $("#videoOffsetY");
  const videoOffsetYValue = $("#videoOffsetYValue");
  const videoBackground = $("#videoBackground");
  const videoTitle = $("#videoTitle");
  const videoConvertButton = $("#videoConvertButton");
  const videoFormError = $("#videoFormError");
  const uploadProgress = $("#uploadProgress");
  const uploadProgressBar = $("#uploadProgressBar");
  const uploadProgressLabel = $("#uploadProgressLabel");
  const previewPlayButton = $("#previewPlayButton");
  const chooseVideoButton = $("#chooseVideoButton");
  const changeVideoButton = $("#changeVideoButton");
  const resetTransformButton = $("#resetTransformButton");
  const telegramDirect = $("#telegramDirect");
  const telegramPublishToken = $("#telegramPublishToken");
  const telegramPublishRemember = $("#telegramPublishRemember");
  const telegramConnectButton = $("#telegramConnectButton");
  const telegramPublishStatus = $("#telegramPublishStatus");
  const telegramStartLink = $("#telegramStartLink");
  const telegramPublishForm = $("#telegramPublishForm");
  const telegramUser = $("#telegramUser");
  const telegramAction = $("#telegramAction");
  const telegramPackFields = $("#telegramPackFields");
  const telegramPackTitleField = $("#telegramPackTitleField");
  const telegramPackTitle = $("#telegramPackTitle");
  const telegramPackName = $("#telegramPackName");
  const telegramPackNameLabel = $("#telegramPackNameLabel");
  const telegramPackNameHelp = $("#telegramPackNameHelp");
  const telegramEmoji = $("#telegramEmoji");
  const telegramPublishButton = $("#telegramPublishButton");
  const telegramResultLink = $("#telegramResultLink");
  const whatsappPackBuilder = $("#whatsappPackBuilder");
  const whatsappPackCount = $("#whatsappPackCount");
  const whatsappAddCurrent = $("#whatsappAddCurrent");
  const whatsappPackStatus = $("#whatsappPackStatus");
  const whatsappPackItems = $("#whatsappPackItems");
  const whatsappPackForm = $("#whatsappPackForm");
  const whatsappPackTitle = $("#whatsappPackTitle");
  const whatsappPackAuthor = $("#whatsappPackAuthor");
  const whatsappBuildPack = $("#whatsappBuildPack");
  const whatsappClearPack = $("#whatsappClearPack");
  const bridgeDownloadButton = $("#bridgeDownloadButton");
  const qrPanelText = $("#qrPanelText");

  let hasSavedToken = false;
  let currentJobId = null;
  let currentShareUrl = "";
  let pollTimer = null;
  let qrObjectUrl = null;
  let pendingRememberToken = false;
  let pendingEphemeralToken = false;
  let currentMode = "pack";
  let selectedVideoFile = null;
  let videoObjectUrl = null;
  let uploadedVideoId = null;
  let uploadedVideoInfo = null;
  let uploadedFileSignature = "";
  let previewLooping = false;
  let dragState = null;
  let currentVideoJob = null;
  let telegramConnection = null;
  let telegramSessionToken = "";

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

  function setMode(mode) {
    if (convertButton.disabled || videoConvertButton.disabled) return;
    currentMode = mode;
    const videoActive = mode === "video";
    packModeButton.classList.toggle("active", !videoActive);
    videoModeButton.classList.toggle("active", videoActive);
    packModeButton.setAttribute("aria-selected", String(!videoActive));
    videoModeButton.setAttribute("aria-selected", String(videoActive));
    form.classList.toggle("hidden", videoActive);
    videoForm.classList.toggle("hidden", !videoActive);
    savedTokenNotice.classList.toggle("hidden", videoActive || !hasSavedToken);
    formatBadge.textContent = videoActive
      ? "WEBM + WebP + MP4"
      : ".wastickers";
    clearError();
    clearVideoError();
  }

  function fileSignature(file) {
    return `${file.name}:${file.size}:${file.lastModified}`;
  }

  function formatClock(seconds) {
    const safe = Math.max(0, Number(seconds) || 0);
    const minutes = Math.floor(safe / 60);
    const rest = safe - minutes * 60;
    return `${minutes}:${rest.toFixed(2).padStart(5, "0")}`;
  }

  function showVideoError(message) {
    videoFormError.textContent = message;
    videoFormError.classList.remove("hidden");
  }

  function clearVideoError() {
    videoFormError.textContent = "";
    videoFormError.classList.add("hidden");
  }

  function showModeError(message) {
    if (currentMode === "video") {
      showVideoError(message);
    } else {
      showError(message);
    }
  }

  function setTelegramStatus(message, isError = false) {
    telegramPublishStatus.textContent = message;
    telegramPublishStatus.classList.toggle("is-error", isError);
  }

  function resetTelegramDirect(job) {
    currentVideoJob = job?.kind === "video" ? job : null;
    telegramConnection = null;
    telegramSessionToken = "";
    telegramDirect.classList.toggle("hidden", !currentVideoJob);
    telegramPublishForm.classList.add("hidden");
    telegramStartLink.classList.add("hidden");
    telegramResultLink.classList.add("hidden");
    telegramResultLink.removeAttribute("href");
    telegramUser.replaceChildren();
    telegramPackTitle.value = currentVideoJob?.title || "";
    telegramPackName.value = "";
    telegramEmoji.value = "✨";
    telegramAction.value = "create";
    telegramConnectButton.textContent = "連接 Telegram Bot";
    setTelegramStatus(
      hasSavedToken
        ? "已找到保存嘅 Bot Token，撳一下連接你嘅 Telegram 帳戶。"
        : "第一次使用請貼上你喺 @BotFather 建立嘅 Bot Token。"
    );
    syncTelegramAction();
  }

  function syncTelegramAction() {
    const action = telegramAction.value;
    telegramPackFields.classList.toggle("hidden", action === "send");
    telegramPackTitleField.classList.toggle("hidden", action !== "create");
    telegramPackTitle.required = action === "create";
    telegramPackName.required = action === "add";
    if (action === "add") {
      telegramPackNameLabel.textContent = "已有貼圖包短名／連結";
      telegramPackNameHelp.textContent =
        "只支援由呢一個 Bot 建立嘅貼圖包";
      telegramPackName.placeholder =
        "例如：https://t.me/addstickers/kosaki_by_MyBot";
      telegramPublishButton.textContent = "加入 Telegram 動態貼圖包";
    } else if (action === "send") {
      telegramPublishButton.textContent = "傳送動態貼圖測試";
    } else {
      telegramPackNameLabel.textContent = "英文短名（可留空）";
      telegramPackNameHelp.textContent =
        "工具會自動加 _by_你個Bot；留空會自動產生";
      telegramPackName.placeholder = "例如：kosaki_motion";
      telegramPublishButton.textContent = "建立並加入 Telegram";
    }
  }

  function setWhatsAppPackStatus(message, isError = false) {
    whatsappPackStatus.textContent = message;
    whatsappPackStatus.classList.toggle("is-error", isError);
  }

  function renderWhatsAppPackState(state) {
    const items = state.items || [];
    whatsappPackCount.textContent = `${items.length} / 30`;
    whatsappBuildPack.disabled = !state.can_build;
    whatsappClearPack.disabled = items.length === 0;
    whatsappPackItems.replaceChildren(
      ...items.map((item, index) => {
        const row = document.createElement("div");
        row.className = "whatsapp-pack-item";
        const number = document.createElement("span");
        number.textContent = String(index + 1).padStart(2, "0");
        const copy = document.createElement("div");
        const title = document.createElement("strong");
        title.textContent = item.title;
        const meta = document.createElement("small");
        meta.textContent = `${item.filename} · ${item.size_label}`;
        copy.append(title, meta);
        const remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "×";
        remove.setAttribute("aria-label", `移除 ${item.title}`);
        remove.addEventListener("click", () => {
          removeWhatsAppPackItem(item.id, remove);
        });
        row.append(number, copy, remove);
        return row;
      })
    );
    if (items.length < 3) {
      setWhatsAppPackStatus(
        `仲差 ${3 - items.length} 張就可以輸出 WhatsApp 動態貼圖包。`
      );
    } else {
      setWhatsAppPackStatus(
        `已準備 ${items.length} 張；可以輸出 .wastickers。`
      );
    }
  }

  async function refreshWhatsAppPack() {
    const state = await api("/api/video-pack");
    renderWhatsAppPackState(state);
    return state;
  }

  async function resetWhatsAppPackBuilder(job) {
    const visible = job?.kind === "video";
    whatsappPackBuilder.classList.toggle("hidden", !visible);
    if (!visible) return;
    if (!whatsappPackTitle.value.trim()) {
      whatsappPackTitle.value = `${job.title || "TGWA"} 動態 Pack`;
    }
    try {
      await refreshWhatsAppPack();
    } catch (error) {
      setWhatsAppPackStatus(error.message, true);
    }
  }

  async function addCurrentToWhatsAppPack() {
    if (!currentVideoJob) return;
    whatsappAddCurrent.disabled = true;
    setWhatsAppPackStatus("正在加入今次 Animated WebP…");
    try {
      const state = await api(
        `/api/jobs/${currentVideoJob.id}/video-pack`,
        { method: "POST", body: "{}" }
      );
      renderWhatsAppPackState(state);
      whatsappAddCurrent.textContent = "✓ 已加入今次貼圖";
      setTimeout(() => {
        whatsappAddCurrent.textContent = "＋ 將今次動態貼圖加入 Pack";
      }, 1600);
    } catch (error) {
      setWhatsAppPackStatus(error.message, true);
    } finally {
      whatsappAddCurrent.disabled = false;
    }
  }

  async function removeWhatsAppPackItem(itemId, button) {
    button.disabled = true;
    try {
      const state = await api("/api/video-pack/remove", {
        method: "POST",
        body: JSON.stringify({ item_id: itemId }),
      });
      renderWhatsAppPackState(state);
    } catch (error) {
      setWhatsAppPackStatus(error.message, true);
      button.disabled = false;
    }
  }

  async function clearWhatsAppPack() {
    if (!window.confirm("清空暫存中嘅 WhatsApp 動態貼圖？")) return;
    try {
      const state = await api("/api/video-pack/clear", {
        method: "POST",
        body: "{}",
      });
      renderWhatsAppPackState(state);
    } catch (error) {
      setWhatsAppPackStatus(error.message, true);
    }
  }

  async function buildWhatsAppPack(event) {
    event.preventDefault();
    whatsappBuildPack.disabled = true;
    setWhatsAppPackStatus("正在封裝 WhatsApp 動態貼圖包…");
    try {
      const job = await api("/api/video-pack/build", {
        method: "POST",
        body: JSON.stringify({
          title: whatsappPackTitle.value.trim(),
          author: whatsappPackAuthor.value.trim(),
        }),
      });
      currentJobId = job.id;
      await renderResult(job);
    } catch (error) {
      setWhatsAppPackStatus(error.message, true);
      whatsappBuildPack.disabled = false;
    }
  }

  async function connectTelegram() {
    const suppliedToken = telegramPublishToken.value.trim();
    telegramConnectButton.disabled = true;
    telegramPublishForm.classList.add("hidden");
    telegramResultLink.classList.add("hidden");
    setTelegramStatus("正在連接 Telegram Bot…");
    try {
      const connection = await api("/api/telegram/connect", {
        method: "POST",
        body: JSON.stringify({
          token: suppliedToken,
          remember: telegramPublishRemember.checked,
        }),
      });
      telegramConnection = connection;
      telegramSessionToken = telegramPublishRemember.checked
        ? ""
        : suppliedToken;
      hasSavedToken = Boolean(
        connection.has_saved_token || hasSavedToken
      );
      if (telegramPublishRemember.checked) {
        telegramPublishToken.value = "";
      }
      updateTokenState();
      telegramStartLink.href = connection.start_url;
      telegramStartLink.textContent =
        `開啟 @${connection.bot_username} 並按 Start`;

      if (!connection.users?.length) {
        telegramStartLink.classList.remove("hidden");
        telegramConnectButton.textContent = "我已按 Start，重新檢查";
        setTelegramStatus(
          `請先開啟 @${connection.bot_username}，按 Start 或傳送 /start，` +
          "再返嚟撳「重新檢查」。"
        );
        return;
      }

      telegramUser.replaceChildren(
        ...connection.users.map((user) => {
          const option = document.createElement("option");
          option.value = String(user.id);
          option.textContent =
            `${user.label}${user.username ? ` (@${user.username})` : ""}`;
          option.dataset.label = user.label;
          return option;
        })
      );
      telegramStartLink.classList.add("hidden");
      telegramPublishForm.classList.remove("hidden");
      telegramConnectButton.textContent = "重新連接／更換帳戶";
      setTelegramStatus(
        `已連接 @${connection.bot_username}。` +
        "下面會直接上載 Telegram WEBM，唔會再經相片選擇器。"
      );
    } catch (error) {
      setTelegramStatus(error.message, true);
    } finally {
      telegramConnectButton.disabled = false;
    }
  }

  async function publishTelegram(event) {
    event.preventDefault();
    if (!currentVideoJob || !telegramConnection) {
      setTelegramStatus("請先連接 Telegram Bot。", true);
      return;
    }
    const selectedUser = telegramUser.selectedOptions[0];
    telegramPublishButton.disabled = true;
    telegramResultLink.classList.add("hidden");
    setTelegramStatus("正在將 WEBM 直接上載去 Telegram…");
    try {
      const result = await api(
        `/api/jobs/${currentVideoJob.id}/telegram`,
        {
          method: "POST",
          body: JSON.stringify({
            token: telegramSessionToken,
            action: telegramAction.value,
            user_id: telegramUser.value,
            user_label: selectedUser?.dataset.label || "已連接帳戶",
            title: telegramPackTitle.value.trim(),
            name: telegramPackName.value.trim(),
            emoji: telegramEmoji.value.trim(),
            remember: telegramPublishRemember.checked,
          }),
        }
      );
      setTelegramStatus(result.message || "已完成 Telegram 上載。");
      if (result.set_url) {
        telegramResultLink.href = result.set_url;
        telegramResultLink.textContent = "打開 Telegram 貼圖包並加入";
        telegramResultLink.classList.remove("hidden");
      }
    } catch (error) {
      setTelegramStatus(error.message, true);
    } finally {
      telegramPublishButton.disabled = false;
      syncTelegramAction();
    }
  }

  function setPreviewButton(playing) {
    const label = previewPlayButton.querySelector("span");
    previewPlayButton.classList.toggle("is-playing", playing);
    if (label) label.textContent = playing ? "暫停預覽" : "預覽剪輯";
  }

  function updateVideoTransform() {
    const scale = Number(videoScale.value) / 100;
    const offsetX = Number(videoOffsetX.value);
    const offsetY = Number(videoOffsetY.value);
    videoPreview.style.left = `${50 + offsetX / 2}%`;
    videoPreview.style.top = `${50 + offsetY / 2}%`;
    videoPreview.style.transform = `translate(-50%, -50%) scale(${scale})`;
    videoScaleValue.textContent = `${Math.round(scale * 100)}%`;
    videoOffsetXValue.textContent = `${offsetX > 0 ? "+" : ""}${offsetX}`;
    videoOffsetYValue.textContent = `${offsetY > 0 ? "+" : ""}${offsetY}`;
  }

  function syncClipBounds(seek = true) {
    const sourceDuration = Number(videoPreview.duration) || 0;
    const maxStart = Math.max(0, sourceDuration - 0.2);
    clipStart.max = String(maxStart);
    if (Number(clipStart.value) > maxStart) {
      clipStart.value = String(maxStart);
    }
    const start = Number(clipStart.value) || 0;
    const maxDuration = Math.max(0.2, Math.min(3, sourceDuration - start));
    clipDuration.max = String(maxDuration);
    if (Number(clipDuration.value) > maxDuration) {
      clipDuration.value = String(maxDuration);
    }
    clipStartValue.textContent = `${start.toFixed(2)} 秒`;
    clipDurationValue.textContent = `${Number(clipDuration.value).toFixed(2)} 秒`;
    if (seek && Number.isFinite(videoPreview.duration)) {
      videoPreview.currentTime = Math.min(start, videoPreview.duration);
    }
  }

  function resetVideoTransform() {
    videoScale.value = "100";
    videoOffsetX.value = "0";
    videoOffsetY.value = "0";
    updateVideoTransform();
  }

  function setVideoFile(file) {
    clearVideoError();
    if (!file) return;
    if (file.size > 512 * 1024 * 1024) {
      showVideoError("影片最多 512 MB。");
      return;
    }
    const extension = `.${file.name.split(".").pop().toLowerCase()}`;
    const allowed = [".mp4", ".mov", ".m4v", ".mkv", ".webm", ".avi", ".gif"];
    if (!allowed.includes(extension)) {
      showVideoError("支援 MP4、MOV、M4V、MKV、WEBM、AVI 或 GIF。");
      return;
    }

    selectedVideoFile = file;
    uploadedVideoId = null;
    uploadedVideoInfo = null;
    uploadedFileSignature = "";
    previewLooping = false;
    videoPreview.pause();
    if (videoObjectUrl) URL.revokeObjectURL(videoObjectUrl);
    videoObjectUrl = URL.createObjectURL(file);
    videoPreview.src = videoObjectUrl;
    videoFileName.textContent = file.name;
    videoFileMeta.textContent = `${(file.size / 1024 / 1024).toFixed(1)} MB · 正在讀取…`;
    videoTitle.value = file.name.replace(/\.[^.]+$/, "").slice(0, 128);
    clipStart.value = "0";
    clipDuration.value = "3";
    videoBackground.value = "transparent";
    videoStage.dataset.background = "transparent";
    resetVideoTransform();
    videoDrop.classList.add("hidden");
    videoEditor.classList.remove("hidden");
    videoPreview.load();
  }

  function uploadVideoFile(file) {
    const signature = fileSignature(file);
    if (
      uploadedVideoId &&
      uploadedVideoInfo &&
      uploadedFileSignature === signature
    ) {
      return Promise.resolve(uploadedVideoInfo);
    }

    uploadProgress.classList.remove("hidden");
    uploadProgressBar.style.width = "0%";
    uploadProgressLabel.textContent = "正在上載影片到本機工具… 0%";
    return new Promise((resolve, reject) => {
      const request = new XMLHttpRequest();
      request.open("POST", "/api/video/upload");
      request.setRequestHeader("X-App-Key", appKey);
      request.setRequestHeader("X-File-Name", encodeURIComponent(file.name));
      request.upload.addEventListener("progress", (event) => {
        if (!event.lengthComputable) return;
        const percent = Math.round((event.loaded / event.total) * 100);
        uploadProgressBar.style.width = `${percent}%`;
        uploadProgressLabel.textContent = `正在上載影片到本機工具… ${percent}%`;
      });
      request.addEventListener("load", () => {
        let payload;
        try {
          payload = JSON.parse(request.responseText);
        } catch {
          reject(new Error("本機工具回傳格式錯誤。"));
          return;
        }
        if (request.status < 200 || request.status >= 300) {
          reject(new Error(payload.error || "影片上載失敗。"));
          return;
        }
        uploadedVideoId = payload.upload_id;
        uploadedVideoInfo = payload;
        uploadedFileSignature = signature;
        uploadProgressBar.style.width = "100%";
        uploadProgressLabel.textContent = "影片已上載到本機工具。";
        resolve(payload);
      });
      request.addEventListener("error", () => {
        reject(new Error("影片上載失敗，請再試。"));
      });
      request.send(file);
    });
  }

  async function startVideoConversion(event) {
    event.preventDefault();
    clearVideoError();
    clearError();
    resetRunUi();
    if (!selectedVideoFile) {
      showVideoError("請先選擇影片。");
      return;
    }

    setFormBusy(true);
    try {
      const upload = await uploadVideoFile(selectedVideoFile);
      const sourceDuration = Number(upload.duration) || 0;
      let start = Number(clipStart.value) || 0;
      let duration = Number(clipDuration.value) || 3;
      if (sourceDuration > 0) {
        start = Math.min(start, Math.max(0, sourceDuration - 0.2));
        duration = Math.min(duration, 3, sourceDuration - start);
        if (duration < 0.2) {
          throw new Error("影片剩餘長度不足 0.2 秒，請調前開始時間。");
        }
        clipStart.max = String(Math.max(0, sourceDuration - 0.2));
        clipStart.value = String(start);
        clipDuration.max = String(Math.min(3, sourceDuration - start));
        clipDuration.value = String(duration);
        clipStartValue.textContent = `${start.toFixed(2)} 秒`;
        clipDurationValue.textContent = `${duration.toFixed(2)} 秒`;
        if (!Number.isFinite(videoPreview.duration)) {
          videoFileMeta.textContent =
            `${upload.width}×${upload.height} · ${formatClock(sourceDuration)} · ` +
            "由轉換引擎讀取";
        }
      }
      const job = await api("/api/video/convert", {
        method: "POST",
        body: JSON.stringify({
          upload_id: upload.upload_id,
          title: videoTitle.value.trim(),
          start,
          duration,
          scale: Number(videoScale.value) / 100,
          offset_x: Number(videoOffsetX.value),
          offset_y: Number(videoOffsetY.value),
          background: videoBackground.value,
        }),
      });
      currentJobId = job.id;
      statusCard.classList.remove("hidden");
      updateProgress(job);
      pollTimer = setTimeout(pollJob, 500);
      statusCard.scrollIntoView({ behavior: "smooth", block: "center" });
    } catch (error) {
      setFormBusy(false);
      showVideoError(error.message);
    }
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
      whatsappPackAuthor.value = config.author || "TGWA Converter";
      updateTokenState();
    } catch {
      fatalOverlay.classList.remove("hidden");
    }
  }

  function updateTokenState() {
    savedTokenNotice.classList.toggle(
      "hidden",
      currentMode === "video" || !hasSavedToken
    );
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
    videoConvertButton.disabled = busy;
    packModeButton.disabled = busy;
    videoModeButton.disabled = busy;
    telegramUrl.disabled = busy;
    botToken.disabled = busy;
    packTitle.disabled = busy;
    packAuthor.disabled = busy;
    rememberToken.disabled = busy;
    videoFile.disabled = busy;
    clipStart.disabled = busy;
    clipDuration.disabled = busy;
    videoScale.disabled = busy;
    videoOffsetX.disabled = busy;
    videoOffsetY.disabled = busy;
    videoBackground.disabled = busy;
    videoTitle.disabled = busy;
    chooseVideoButton.disabled = busy;
    changeVideoButton.disabled = busy;
    previewPlayButton.disabled = busy;
    resetTransformButton.disabled = busy;
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

    const phaseLabels =
      job.kind === "video"
        ? {
            validate: ["CHECKING", "正在檢查影片設定…"],
            download: ["TRIMMING", "正在擷取及排版影片…"],
            convert: ["OPTIMIZING", "正在搜尋限制內最高畫質…"],
            package: ["VERIFYING", "正在核對兩邊貼圖規格…"],
            share: ["READY", "動態貼圖完成。"],
          }
        : {
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
      showModeError(error.message);
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
      showModeError(error.message);
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
    if (item.label) {
      meta.textContent = `${item.label} · ${item.dimensions || "512×512"}`;
    } else {
      const kind =
        item.kind === "animated"
          ? "動態"
          : item.kind === "static"
          ? "靜態"
          : "";
      meta.textContent = `${item.sticker_count} 張${kind}貼圖`;
    }
    copy.append(name, meta);
    if (item.platform) {
      const platform = document.createElement("span");
      platform.className = "platform-badge";
      platform.textContent = item.platform;
      copy.append(platform);
    }
    if (item.import_hint) {
      const hint = document.createElement("small");
      hint.className = "import-hint";
      hint.textContent = item.import_hint;
      copy.append(hint);
    }

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
      showModeError(error.message);
      button.textContent = original;
    } finally {
      button.disabled = false;
    }
  }

  async function renderResult(job) {
    currentShareUrl = job.share_url || "";
    const items = job.outputs || job.packages || [];
    const packKinds = new Set(items.map((item) => item.kind).filter(Boolean));
    const separatedKinds =
      packKinds.has("animated") && packKinds.has("static");
    resultSummary.textContent =
      job.kind === "video"
        ? "已輸出 Telegram WEBM、WhatsApp 動態 WebP，同埋 Sticker Maker MP4 匯入片。"
        : job.kind === "whatsapp_pack"
        ? `已封裝 ${items[0]?.sticker_count || 0} 張 WhatsApp 動態貼圖；用 TGWA Bridge 開啟即可加入。`
        : separatedKinds
        ? `已按 WhatsApp 規格分開靜態／動態，共製作 ${items.length} 個貼圖包。`
        : `已製作 ${items.length} 個貼圖包；手機掃碼即可下載。`;
    packageList.replaceChildren(
      ...items.map((item, index) => packageRow(item, index))
    );
    resetTelegramDirect(job);
    await resetWhatsAppPackBuilder(job);
    const needsBridge = job.kind !== "video";
    bridgeDownloadButton.classList.toggle("hidden", !needsBridge);
    qrPanelText.textContent = needsBridge
      ? "同一個 Wi-Fi 下掃碼下載，再用 TGWA Bridge 加入 WhatsApp。"
      : "Telegram 可直接由工具上載；WhatsApp 可先將 3–30 張加入動態 Pack。";

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
  videoForm.addEventListener("submit", startVideoConversion);
  telegramPublishForm.addEventListener("submit", publishTelegram);
  telegramConnectButton.addEventListener("click", connectTelegram);
  telegramAction.addEventListener("change", syncTelegramAction);
  whatsappAddCurrent.addEventListener(
    "click",
    addCurrentToWhatsAppPack
  );
  whatsappPackForm.addEventListener("submit", buildWhatsAppPack);
  whatsappClearPack.addEventListener("click", clearWhatsAppPack);

  packModeButton.addEventListener("click", () => setMode("pack"));
  videoModeButton.addEventListener("click", () => setMode("video"));

  chooseVideoButton.addEventListener("click", () => videoFile.click());
  changeVideoButton.addEventListener("click", () => videoFile.click());
  videoFile.addEventListener("change", () => {
    setVideoFile(videoFile.files?.[0] || null);
    videoFile.value = "";
  });

  ["dragenter", "dragover"].forEach((eventName) => {
    videoDrop.addEventListener(eventName, (event) => {
      event.preventDefault();
      videoDrop.classList.add("is-dragging");
    });
  });
  ["dragleave", "drop"].forEach((eventName) => {
    videoDrop.addEventListener(eventName, (event) => {
      event.preventDefault();
      videoDrop.classList.remove("is-dragging");
    });
  });
  videoDrop.addEventListener("drop", (event) => {
    setVideoFile(event.dataTransfer?.files?.[0] || null);
  });
  videoDrop.addEventListener("click", (event) => {
    if (!event.target.closest("button")) videoFile.click();
  });

  videoPreview.addEventListener("loadedmetadata", () => {
    const width = Number(videoPreview.videoWidth) || 0;
    const height = Number(videoPreview.videoHeight) || 0;
    const duration = Number(videoPreview.duration) || 0;
    videoPreview.classList.toggle("is-portrait", height > width);
    videoFileMeta.textContent =
      `${width}×${height} · ${formatClock(duration)} · 最長剪 3 秒`;
    if (duration < 0.2) {
      showVideoError("影片長度最少要 0.2 秒。");
      videoConvertButton.disabled = true;
      return;
    }
    videoConvertButton.disabled = false;
    clipStart.value = "0";
    clipDuration.value = String(Math.min(3, duration));
    syncClipBounds();
  });
  videoPreview.addEventListener("error", () => {
    videoFileMeta.textContent = "瀏覽器未能預覽；仍可嘗試由轉換引擎讀取";
  });

  clipStart.addEventListener("input", () => {
    previewLooping = false;
    videoPreview.pause();
    setPreviewButton(false);
    syncClipBounds();
  });
  clipDuration.addEventListener("input", () => {
    clipDurationValue.textContent =
      `${Number(clipDuration.value).toFixed(2)} 秒`;
  });
  [videoScale, videoOffsetX, videoOffsetY].forEach((input) => {
    input.addEventListener("input", updateVideoTransform);
  });
  videoBackground.addEventListener("change", () => {
    videoStage.dataset.background = videoBackground.value;
  });
  resetTransformButton.addEventListener("click", resetVideoTransform);

  previewPlayButton.addEventListener("click", async () => {
    if (!selectedVideoFile) return;
    if (previewLooping && !videoPreview.paused) {
      previewLooping = false;
      videoPreview.pause();
      setPreviewButton(false);
      return;
    }
    previewLooping = true;
    const start = Number(clipStart.value) || 0;
    const end = start + (Number(clipDuration.value) || 0.2);
    if (
      videoPreview.currentTime < start ||
      videoPreview.currentTime >= end - 0.03
    ) {
      videoPreview.currentTime = start;
    }
    try {
      await videoPreview.play();
      setPreviewButton(true);
    } catch {
      previewLooping = false;
      setPreviewButton(false);
      showVideoError("瀏覽器未能播放預覽，但仍可直接轉換。");
    }
  });
  videoPreview.addEventListener("timeupdate", () => {
    if (!previewLooping) return;
    const start = Number(clipStart.value) || 0;
    const end = start + (Number(clipDuration.value) || 0.2);
    if (videoPreview.currentTime >= end) {
      videoPreview.currentTime = start;
      videoPreview.play().catch(() => {
        previewLooping = false;
        setPreviewButton(false);
      });
    }
  });
  videoPreview.addEventListener("pause", () => {
    if (!previewLooping) setPreviewButton(false);
  });

  videoStage.addEventListener("pointerdown", (event) => {
    if (!selectedVideoFile) return;
    dragState = {
      pointerId: event.pointerId,
      x: event.clientX,
      y: event.clientY,
      offsetX: Number(videoOffsetX.value),
      offsetY: Number(videoOffsetY.value),
    };
    videoStage.setPointerCapture(event.pointerId);
  });
  videoStage.addEventListener("pointermove", (event) => {
    if (!dragState || dragState.pointerId !== event.pointerId) return;
    const rect = videoStage.getBoundingClientRect();
    const clamp = (value) => Math.max(-100, Math.min(100, value));
    videoOffsetX.value = String(
      Math.round(clamp(dragState.offsetX + ((event.clientX - dragState.x) / rect.width) * 200))
    );
    videoOffsetY.value = String(
      Math.round(clamp(dragState.offsetY + ((event.clientY - dragState.y) / rect.height) * 200))
    );
    updateVideoTransform();
  });
  const finishVideoDrag = (event) => {
    if (!dragState || dragState.pointerId !== event.pointerId) return;
    if (videoStage.hasPointerCapture(event.pointerId)) {
      videoStage.releasePointerCapture(event.pointerId);
    }
    dragState = null;
  };
  videoStage.addEventListener("pointerup", finishVideoDrag);
  videoStage.addEventListener("pointercancel", finishVideoDrag);

  window.addEventListener("beforeunload", () => {
    if (videoObjectUrl) URL.revokeObjectURL(videoObjectUrl);
  });

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
      showModeError(error.message);
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
      showModeError(error.message);
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
      showModeError(error.message);
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
      showModeError(`請手動複製：${currentShareUrl}`);
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
