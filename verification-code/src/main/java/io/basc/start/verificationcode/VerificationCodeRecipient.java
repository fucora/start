package io.basc.start.verificationcode;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VerificationCodeRecipient implements Serializable {
	private static final long serialVersionUID = 1L;
	/**
	 * 用户
	 */
	private String user;
	/**
	 * 类型
	 */
	private String type;
}
