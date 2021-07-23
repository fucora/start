package scw.integration.bytedance.poi;

import io.swagger.v3.oas.annotations.media.Schema;
import scw.integration.bytedance.oauth.ClientRequest;

public class PoiQueryRequest extends ClientRequest {
	private static final long serialVersionUID = 1L;
	@Schema(required = true)
	private String amap_id;

	public String getAmap_id() {
		return amap_id;
	}

	public void setAmap_id(String amap_id) {
		this.amap_id = amap_id;
	}
}
