package scw.integration.tencent.trade;

import java.io.IOException;
import java.util.Map;

import scw.context.annotation.Provider;
import scw.core.Ordered;
import scw.core.utils.StringUtils;
import scw.integration.tencent.wx.pay.RefundRequest;
import scw.integration.tencent.wx.pay.Unifiedorder;
import scw.integration.tencent.wx.pay.UnifiedorderRequest;
import scw.integration.tencent.wx.pay.WeiXinPay;
import scw.integration.tencent.wx.pay.WeiXinPayResponse;
import scw.integration.trade.TradeException;
import scw.integration.trade.TradeStatus;
import scw.integration.trade.create.TradeCreate;
import scw.integration.trade.create.TradeCreateAdapter;
import scw.integration.trade.create.TradeCreateResponse;
import scw.integration.trade.refund.TradeRefund;
import scw.integration.trade.refund.TradeRefundAdapter;
import scw.integration.trade.status.TradeResultsEvent;
import scw.integration.trade.status.TradeStatusDispatcher;
import scw.integration.trade.web.TradeNotifyAdapter;
import scw.integration.trade.web.TradeNotifyConfig;
import scw.json.JSONUtils;
import scw.logger.Logger;
import scw.logger.LoggerFactory;
import scw.mapper.Copy;
import scw.util.DefaultStatus;
import scw.util.Status;
import scw.web.ServerHttpRequest;

@Provider(order = Ordered.LOWEST_PRECEDENCE)
public class WeixinPaymentAdapter implements TradeCreateAdapter, TradeNotifyAdapter, TradeRefundAdapter {
	private static Logger logger = LoggerFactory.getLogger(WeixinPaymentAdapter.class);

	public static final String WEB_TRADE_METHOD = "WX_JSAPI";
	public static final String APP_TRADE_METHOD = "WX_APP";

	public static final String OPENID_KEY = "open_id";

	private static final String SUCCESS_TEXT = "SUCCESS";

	private final WeiXinPay weiXinPay;
	private final TradeNotifyConfig notifyConfig;

	public WeixinPaymentAdapter(TradeNotifyConfig notifyConfig, WeiXinPay weiXinPay) {
		this.notifyConfig = notifyConfig;
		this.weiXinPay = weiXinPay;
	}

	@Override
	public boolean accept(String paymentMethod) {
		return WEB_TRADE_METHOD.equals(paymentMethod) || APP_TRADE_METHOD.equals(paymentMethod);
	}

	@Override
	public TradeCreateResponse create(TradeCreate request) throws TradeException {
		String type = "JSAPI";
		if (APP_TRADE_METHOD.equals(request.getTradeMethod())) {
			type = "APP";
		}

		UnifiedorderRequest unifiedorderRequest = new UnifiedorderRequest(request.getSubject(), request.getTradeNo(),
				request.getTradeAmount(), request.getIp(),
				notifyConfig.getNotifyUrl(request.getTradeMethod(), TradeStatus.SUCCESS), type);
		String openid = request.getExtended().get(OPENID_KEY);
		unifiedorderRequest.setOpenid(openid);
		Unifiedorder unifiedorder = weiXinPay.payment(unifiedorderRequest);
		TradeCreateResponse response = new TradeCreateResponse();
		Copy.copy(response, request);
		response.setCredential(unifiedorder);
		return response;
	}

	@Override
	public boolean refund(TradeRefund request) throws TradeException {
		RefundRequest refundRequest = new RefundRequest();
		refundRequest.setNotify_url(notifyConfig.getNotifyUrl(request.getTradeMethod(), TradeStatus.REFUND));
		refundRequest.setTransaction_id(request.getThirdpartyTradeNo());
		refundRequest.setOut_refund_no(request.getTradeNo());
		refundRequest.setRefund_desc(request.getSubject());
		refundRequest.setRefund_fee(request.getTradeAmount());
		refundRequest.setTotal_fee(request.getTotalTradeAmount());

		WeiXinPayResponse response = weiXinPay.refund(refundRequest);
		if (response.isSuccess()) {
			return true;
		}
		logger.error("申请退款异常:" + response);
		return false;
	}

	@Override
	public Object notify(String tradeMethod, String tradeStatus, ServerHttpRequest request,
			TradeStatusDispatcher dispatcher) throws TradeException {
		XmlMap map;
		try {
			map = new XmlMap(request);
		} catch (IOException e) {
			throw new TradeException(e);
		}

		logger.info("收到微信支付回调:\n" + JSONUtils.getJsonSupport().toJSONString(map));
		Status<String> status = check(map);
		if (!status.isActive()) {
			logger.error("微信支付回调失败：{}", status.get());
			return status.get();
		}

		TradeResultsEvent event = new TradeResultsEvent();
		event.setTradeMethod(tradeMethod);
		event.setCreateTime(System.currentTimeMillis());
		event.setExtended(map);
		if (TradeStatus.SUCCESS.equals(tradeStatus)) {
			event.setThirdpartyTradeNo(map.get("transaction_id"));
			event.setTradeNo(map.get("out_trade_no"));
			event.setTradeAmount(Integer.parseInt(map.get("total_fee")));
			event.setSuccess(true);
		} else if (TradeStatus.REFUND.equals(tradeStatus)) {
			event.setThirdpartyTradeNo(map.get("refund_id"));
			event.setTradeNo(map.get("out_refund_no"));
			event.setTradeAmount(Integer.parseInt(map.get("settlement_refund_fee")));
			event.setSuccess("SUCCESS".equals(map.get("refund_status")));
		}
		dispatcher.publishEvent(tradeStatus, event);
		return SUCCESS_TEXT;
	}

	public Status<String> check(Map<String, String> map) {
		if (!SUCCESS_TEXT.equals(map.get("return_code"))) {
			return new DefaultStatus<String>(false, map.get("return_msg"));
		}

		if (!SUCCESS_TEXT.equals(map.get("result_code"))) {
			return new DefaultStatus<String>(false, map.get("err_code") + "(" + map.get("err_code_des") + ")");
		}

		String out_trade_no = map.get("out_trade_no");
		if (StringUtils.isEmpty(out_trade_no)) {
			return new DefaultStatus<String>(false, "订单号错误");
		}

		boolean success = weiXinPay.checkSign(map);
		if (!success) {
			return new DefaultStatus<String>(false, "签名错误");
		}
		return new DefaultStatus<String>(true, "SUCCESS");
	}
}
