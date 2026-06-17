function getMainUrl(fullUrl) {
  fullUrl = fullUrl || window.location.href;
  // const fullUrl = window.location.href;
  // 初始化默认基础URL
  let mainUrl = "http://localhost:8005/";
  // 处理当前页面URL（如果是http开头）
  if (fullUrl.startsWith("http")) {
    mainUrl = fullUrl.split("?")[0];
  }
  // 处理URL参数中的url（优先级更高）
  const urlParams = new URLSearchParams(window.location.search);
  const urlParam = urlParams.get("url");
  const redirectParam = urlParams.get("redirect");

  if (urlParam) {
    // 解码参数中的URL（支持http开头的明文和base64编码）
    mainUrl = urlParam.startsWith("http")
      ? decodeURIComponent(urlParam)
      : atob(urlParam);
  } else if (redirectParam) {
    try {
      mainUrl = getRedirectConfig(redirectParam).redirectUrl;
    } catch (e) {
      console.error(e);
    }
  }

  try {
    if (fullUrl.startsWith("http") && !window.xxxr) {
      const configUrl =
        "https://zhangheng0805.github.io/LiveMonitoringRecordingPage/redirect/json/config.json";
      getRedirectConfig(configUrl);
    }
  } catch (e) {
    console.error(e);
  }
  // 确保URL以斜杠结尾
  let rootURl = mainUrl.endsWith("/") ? mainUrl : `${mainUrl}/`;
  window.MainUrl = rootURl;
  return rootURl;
}

function getRedirectConfig(config_url) {
  const xhr = new XMLHttpRequest();
  xhr.open("GET", config_url + "?_t=" + new Date().getTime(), false); // false = 同步阻塞
  xhr.send(null);

  if (xhr.status >= 200 && xhr.status < 300) {
    let config = JSON.parse(xhr.responseText);
    window.xxxr = config;
    return config;
  } else {
    throw new Error("获取getRedirectConfig失败" + xhr);
  }
}
