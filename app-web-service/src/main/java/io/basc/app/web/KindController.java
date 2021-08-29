package io.basc.app.web;

import io.basc.framework.http.HttpMethod;
import io.basc.framework.io.IOUtils;
import io.basc.framework.mvc.annotation.Controller;
import io.basc.framework.net.message.multipart.MultipartMessage;
import io.basc.framework.security.session.UserSession;
import io.basc.framework.upload.kind.KindDirType;
import io.basc.framework.upload.kind.KindEditor;
import io.basc.framework.upload.kind.KindOrderType;
import io.basc.framework.web.MultiPartServerHttpRequest;

import java.util.HashMap;
import java.util.Map;

@Controller(value = "kind")
public class KindController {
	private KindEditor kindEditor;

	public KindController(KindEditor kindEditor) {
		this.kindEditor = kindEditor;
	}

	public String getRequestGroup(UserSession<Long> requestUser) {
		return requestUser != null? (requestUser.getUid() + "") : null;
	}

	@Controller(value = "upload", methods = { HttpMethod.POST, HttpMethod.PUT })
	public Object upload(UserSession<Long> requestUser, MultiPartServerHttpRequest request, KindDirType dir) {
		MultipartMessage fileItem = request.getFirstFile();
		if (fileItem == null) {
			return error("请选择文件");
		}

		try {
			String url = kindEditor.upload(getRequestGroup(requestUser), dir, fileItem);
			return success(url);
		} catch (Exception e) {
			return error(e.getMessage());
		} finally {
			IOUtils.closeQuietly(fileItem);
		}
	}

	@Controller(value = "manager", methods = { HttpMethod.GET })
	public Object manager(UserSession<Long> requestUser, KindDirType dir, String path, KindOrderType order) {
		return kindEditor.manager(getRequestGroup(requestUser), dir, path, order);
	}

	private Object success(String url) {
		Map<String, Object> map = new HashMap<String, Object>(4);
		map.put("code", 0);
		map.put("url", url);
		return map;
	}

	private Object error(String msg) {
		Map<String, Object> map = new HashMap<String, Object>(4);
		map.put("code", 1);
		map.put("msg", msg);
		return map;
	}
}
