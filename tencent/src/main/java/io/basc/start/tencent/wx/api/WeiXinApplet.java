package io.basc.start.tencent.wx.api;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.basc.framework.codec.support.AES;
import io.basc.framework.codec.support.Base64;
import io.basc.framework.codec.support.CharsetCodec;
import io.basc.framework.http.MediaType;
import io.basc.framework.json.JSONUtils;
import io.basc.framework.json.JsonObject;
import io.basc.framework.logger.Logger;
import io.basc.framework.logger.LoggerFactory;
import io.basc.framework.util.StringUtils;
import io.basc.start.tencent.wx.WeiXinException;

public class WeiXinApplet extends ClusterWeiXinApi {
	private static Logger logger = LoggerFactory.getLogger(WeiXinApplet.class);

	public WeiXinApplet(String appid, String appsecret) {
		super(appid, appsecret);
	}

	public Session jscode2session(String js_code) {
		StringBuilder sb = new StringBuilder("https://api.weixin.qq.com/sns/jscode2session");
		sb.append("?appid=").append(getAppid());
		sb.append("&secret=").append(getAppsecret());
		sb.append("&js_code=").append(js_code);
		sb.append("&grant_type=authorization_code");
		JsonObject json = doGet(sb.toString());
		return new Session(json);
	}

	public static PhoneNumber decrypt(String encryptedData, String key, String iv) {
		byte[] keyBytes = Base64.DEFAULT.decode(key);
		byte[] ivKeys = Base64.DEFAULT.decode(iv);
		String content = Base64.DEFAULT.toDecoder(new AES(AES.NO_PADDING, keyBytes, ivKeys))
				.toDecoder(CharsetCodec.UTF_8).decode(encryptedData);
		return JSONUtils.getJsonSupport().parseObject(content, PhoneNumber.class);
	}

	public final void uniformSendMessage(String touser, WeappTemplateMsg weapp_template_msg,
			MpTemplateMsg mp_template_msg) throws WeiXinApiException {
		Map<String, Object> map = new HashMap<String, Object>(4, 1);
		map.put("touser", touser);
		map.put("weapp_template_msg", weapp_template_msg);
		map.put("mp_template_msg", mp_template_msg);
		processWithClientCredential((token) -> {
			return doPost("https://api.weixin.qq.com/cgi-bin/message/wxopen/template/uniform_send?access_token="
					+ token.getToken(), map);
		});
	}

	/**
	 * https://developers.weixin.qq.com/miniprogram/dev/api-backend/open-api/updatable-message/updatableMessage.createActivityId.html
	 * <br/>
	 * 创建被分享动态消息的 activity_id
	 */
	public final CreateActivityIdResponse createMessageActivityId() {
		return processWithClientCredential((token) -> {
			JsonObject json = doPost("https://api.weixin.qq.com/cgi-bin/message/wxopen/activityid/create?access_token="
					+ token.getToken(), null);
			return new CreateActivityIdResponse(json);
		});
	}

	/**
	 * @param activity_id    动态消息的 ID，通过 createActivityId 接口获取
	 * @param target_state   动态消息修改后的状态（具体含义见后文）
	 * @param parameter_list 动态消息对应的模板信息
	 */
	public final void sendUpdatablemsg(String activity_id, TargetState target_state,
			EnumMap<TemplateParameterName, String> parameter_list) throws WeiXinApiException {
		Map<String, Object> map = new HashMap<String, Object>(4, 1);
		map.put("activity_id", activity_id);
		map.put("target_state", target_state.getState());

		Map<String, Object> template_info = new HashMap<String, Object>(2);
		List<Object> list = new ArrayList<Object>();
		for (Entry<TemplateParameterName, String> entry : parameter_list.entrySet()) {
			Map<String, Object> json = new HashMap<String, Object>(2, 1);
			json.put("name", entry.getKey().name());
			json.put("value", entry.getValue());
			list.add(json);
		}
		template_info.put("parameter_list", list);
		map.put("template_info", template_info);
		processWithClientCredential((token) -> {
			return doPost("https://api.weixin.qq.com/cgi-bin/message/wxopen/updatablemsg/send?access_token="
					+ token.getToken(), map);
		});
	}

	public JsonObject doPostJson(String url, Object json) {
		String content = getHttpClient().post(String.class, url, json, MediaType.APPLICATION_JSON_UTF8).getBody();
		if (logger.isDebugEnabled()) {
			logger.debug("request:{}, formData={}, response:{}", url, json, content);
		}
		return parseJson(content);
	}

	public String generateUrllink(String accessToken, GenerateUrlRequest request) throws WeiXinException {
		Map<String, Object> map = new HashMap<String, Object>(8);
		if (StringUtils.isNotEmpty(request.getPath())) {
			map.put("path", request.getPath());
		}

		if (StringUtils.isNotEmpty(request.getQuery())) {
			map.put("query", request.getQuery());
		}

		if (StringUtils.isNotEmpty(request.getEnvVersion())) {
			map.put("env_version", request.getEnvVersion());
		}

		map.put("is_expire", request.isExpire());

		if (request.getExpireType() != null) {
			map.put("expire_type", request.getExpireType());
		}

		if (request.getExpireTime() != null) {
			map.put("expire_time", request.getExpireTime());
		}

		if (request.getExpireInterval() != null) {
			map.put("expire_interval", request.getExpireInterval());
		}

		JsonObject response = doPostJson("https://api.weixin.qq.com/wxa/generate_urllink?access_token=" + accessToken,
				map);
		return response.getString("url_link");
	}

	public String generateUrllink(GenerateUrlRequest request) throws WeiXinException {
		return processWithClientCredential((token) -> generateUrllink(token.getToken(), request));
	}

	public String generatescheme(String accessToken, GenerateUrlRequest request) throws WeiXinException {
		Map<String, Object> jumpWxaMap = new HashMap<String, Object>(4);
		if (StringUtils.isNotEmpty(request.getPath())) {
			jumpWxaMap.put("path", request.getPath());
		}

		if (StringUtils.isNotEmpty(request.getQuery())) {
			jumpWxaMap.put("query", request.getQuery());
		}

		if (StringUtils.isNotEmpty(request.getEnvVersion())) {
			jumpWxaMap.put("env_version", request.getEnvVersion());
		}

		Map<String, Object> map = new HashMap<String, Object>(8);
		if (!jumpWxaMap.isEmpty()) {
			map.put("jump_wxa", jumpWxaMap);
		}

		map.put("is_expire", request.isExpire());
		if (request.getExpireType() != null) {
			map.put("expire_type", request.getExpireType());
		}

		if (request.getExpireTime() != null) {
			map.put("expire_time", request.getExpireTime());
		}

		if (request.getExpireInterval() != null) {
			map.put("expire_interval", request.getExpireInterval());
		}

		JsonObject response = doPostJson("https://api.weixin.qq.com/wxa/generatescheme?access_token=" + accessToken,
				map);
		return response.getString("openlink");
	}

	public String generatescheme(GenerateUrlRequest request) throws WeiXinException {
		return processWithClientCredential((token) -> generatescheme(token.getToken(), request));
	}
}
