package io.basc.app.web;

import io.basc.app.user.enums.AccountType;
import io.basc.app.user.model.UserAttributeModel;
import io.basc.app.user.pojo.User;
import io.basc.app.user.security.LoginRequired;
import io.basc.app.user.security.UserLoginService;
import io.basc.app.user.service.UserService;
import io.basc.framework.beans.annotation.Autowired;
import io.basc.framework.context.result.Result;
import io.basc.framework.context.result.ResultFactory;
import io.basc.framework.core.utils.StringUtils;
import io.basc.framework.http.HttpMethod;
import io.basc.framework.mvc.HttpChannel;
import io.basc.framework.mvc.annotation.Controller;
import io.basc.framework.security.session.UserSession;
import io.basc.framework.web.message.annotation.RequestBody;

import java.util.Map;

@Controller(value = "user", methods = { HttpMethod.GET, HttpMethod.POST })
public class UserController {
	private UserService userService;
	@Autowired
	private ResultFactory resultFactory;
	@Autowired
	private UserLoginService userControllerService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Controller(value = "login")
	public Result login(String username, String password, HttpChannel httpChannel) {
		if (StringUtils.isEmpty(username, password)) {
			return resultFactory.parameterError();
		}

		User user = userService.getUserByAccount(AccountType.USERNAME, username);
		if (user == null) {
			user = userService.getUserByAccount(AccountType.PHONE, username);
		}

		if (user == null) {
			return resultFactory.error("账号或密码错误");
		}

		Result result = userService.checkPassword(user.getUid(), password);
		if (result.isError()) {
			return result;
		}

		Map<String, Object> infoMap = userControllerService.login(user, httpChannel);
		return resultFactory.success(infoMap);
	}

	@Controller(value = "update")
	@LoginRequired
	public Result updateUserInfo(UserSession<Long> requestUser, @RequestBody UserAttributeModel userAttributeModel) {
		return userService.updateUserAttribute(requestUser.getUid(), userAttributeModel);
	}

	@Controller(value = "register")
	public Result register(String username, String password, @RequestBody UserAttributeModel userAttributeModel) {
		if (StringUtils.isEmpty(username, password)) {
			return resultFactory.parameterError();
		}

		return userService.register(AccountType.USERNAME, username, password, userAttributeModel);
	}

	@LoginRequired
	@Controller(value = "info")
	public Result info(UserSession<Long> requestUser) {
		User user = userService.getUser(requestUser.getUid());
		if (user == null) {
			return resultFactory.error("用户不存在");
		}

		Map<String, Object> map = userControllerService.info(user);
		return resultFactory.success(map);
	}
}