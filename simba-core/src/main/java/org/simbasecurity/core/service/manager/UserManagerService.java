/*
 * Copyright 2011 Simba Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.simbasecurity.core.service.manager;

import static org.simbasecurity.common.request.RequestConstants.SIMBA_SSO_TOKEN;
import static org.simbasecurity.core.config.ConfigurationParameter.PASSWORD_CHANGE_REQUIRED;
import static org.simbasecurity.core.exception.SimbaMessageKey.USER_ALREADY_EXISTS;
import static org.simbasecurity.core.service.ErrorSender.NO_USER_FOUND_ERROR_CODE;
import static org.simbasecurity.core.service.ErrorSender.UNABLE_TO_CHANGE_PASSWORD_ERROR_CODE;
import static org.simbasecurity.core.service.ErrorSender.UNABLE_TO_RESET_PASSWORD_ERROR_CODE;
import static org.simbasecurity.core.service.ErrorSender.sendError;
import static org.simbasecurity.core.service.ErrorSender.sendUnauthorizedError;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.simbasecurity.api.service.thrift.AuthorizationService;
import org.simbasecurity.api.service.thrift.SSOToken;
import org.simbasecurity.core.config.ConfigurationService;
import org.simbasecurity.core.domain.Role;
import org.simbasecurity.core.domain.Session;
import org.simbasecurity.core.domain.User;
import org.simbasecurity.core.domain.repository.GroupRepository;
import org.simbasecurity.core.domain.repository.PolicyRepository;
import org.simbasecurity.core.domain.repository.RoleRepository;
import org.simbasecurity.core.domain.repository.SessionRepository;
import org.simbasecurity.core.domain.repository.UserRepository;
import org.simbasecurity.core.exception.SimbaException;
import org.simbasecurity.core.service.manager.assembler.GroupDTOAssembler;
import org.simbasecurity.core.service.manager.assembler.PolicyDTOAssembler;
import org.simbasecurity.core.service.manager.assembler.RoleDTOAssembler;
import org.simbasecurity.core.service.manager.assembler.UserAssembler;
import org.simbasecurity.core.service.manager.assembler.UserDTOAssembler;
import org.simbasecurity.core.service.manager.dto.ChangePasswordDTO;
import org.simbasecurity.core.service.manager.dto.GroupDTO;
import org.simbasecurity.core.service.manager.dto.PolicyDTO;
import org.simbasecurity.core.service.manager.dto.RoleDTO;
import org.simbasecurity.core.service.manager.dto.UserDTO;
import org.simbasecurity.core.service.manager.interceptor.ManagerSecurityInterceptor;
import org.simbasecurity.core.service.manager.web.JsonBody;
import org.simbasecurity.core.service.validation.DTOValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Called from the manager GUI. This service is guarded by the
 * {@link ManagerSecurityInterceptor}. The chain called there contains a Command
 * that check if you are admin.
 */
@Transactional
@Controller
@RequestMapping("user")
public class UserManagerService {

	@Autowired
	private AuthorizationService.Iface authorizationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PolicyRepository policyRepository;

	@Autowired
	private GroupRepository groupRepository;

	@Autowired
	private SessionRepository sessionRepository;

	@Autowired
	private ConfigurationService configurationService;

	@RequestMapping("findAll")
	@ResponseBody
	public Collection<UserDTO> findAll() {
		return UserDTOAssembler.assemble(userRepository.findAll());
	}

	@RequestMapping("findByRole")
	@ResponseBody
	public Collection<UserDTO> find(@RequestBody RoleDTO role) {
		return UserDTOAssembler.assemble(roleRepository.lookUp(role).getUsers());
	}

	@RequestMapping("findRoles")
	@ResponseBody
	public Collection<RoleDTO> findRoles(@RequestBody UserDTO user) {
		return RoleDTOAssembler.assemble(userRepository.lookUp(user).getRoles());
	}

	@RequestMapping("findRolesNotLinked")
	@ResponseBody
	public Collection<RoleDTO> findRolesNotLinked(@RequestBody UserDTO user) {
		return RoleDTOAssembler.assemble(roleRepository.findNotLinked(userRepository.lookUp(user)));
	}

	@RequestMapping("removeRole")
	public void removeRole(@JsonBody("user") UserDTO user, @JsonBody("role") RoleDTO role) {
		User attachedUser = userRepository.refreshWithOptimisticLocking(user);
		Role attachedRole = roleRepository.refreshWithOptimisticLocking(role);

		attachedUser.removeRole(attachedRole);
	}

	@RequestMapping("addRoles")
	public void addRoles(@JsonBody("user") UserDTO user, @JsonBody("roles") Set<RoleDTO> roles) {
		User attachedUser = userRepository.refreshWithOptimisticLocking(user);
		Collection<Role> attachedRoles = roleRepository.refreshWithOptimisticLocking(roles);

		attachedUser.addRoles(attachedRoles);
	}

	@RequestMapping("findPolicies")
	@ResponseBody
	public Collection<PolicyDTO> findPolicies(@RequestBody UserDTO user) {
		return PolicyDTOAssembler.assemble(policyRepository.find(userRepository.lookUp(user)));
	}

	@RequestMapping("findGroups")
	@ResponseBody
	public Collection<GroupDTO> findGroups(@RequestBody UserDTO user) {
		return GroupDTOAssembler.assemble(groupRepository.find(userRepository.lookUp(user)));
	}

	@RequestMapping("resetPassword")
	@ResponseBody
	public UserDTO resetPassword(@RequestBody UserDTO user, HttpServletResponse response) {
		User attachedUser = userRepository.refreshWithOptimisticLocking(user);

		try {
			attachedUser.resetPassword();
		} catch (SimbaException ex) {
			sendError(UNABLE_TO_RESET_PASSWORD_ERROR_CODE, response,
					"Something went wrong while resetting the password of user '" + attachedUser.getUserName() + "'. Message: " + ex.getMessage());
		}

		userRepository.flush();
		return UserDTOAssembler.assemble(attachedUser);
	}

	@RequestMapping("changePassword")
	@ResponseBody
	public void changeUserPassword(@RequestHeader(value = SIMBA_SSO_TOKEN, required = false) String ssoTokenFromHeader,
			@CookieValue(value = SIMBA_SSO_TOKEN, required = false) String ssoTokenFromCookie, @RequestBody ChangePasswordDTO changePasswordDTO,
			HttpServletResponse response) {

		String ssoToken = (ssoTokenFromHeader != null ? ssoTokenFromHeader : ssoTokenFromCookie);
		if (ssoToken == null) {
			sendUnauthorizedError(response);
			return;
		}

		Session activeSession = sessionRepository.findBySSOToken(new SSOToken(ssoToken));
		if (activeSession == null) {
			sendUnauthorizedError(response);
			return;

		} else {

			User attachedUser = userRepository.findByName(changePasswordDTO.getUserName());
			if (attachedUser == null) {
				sendError(NO_USER_FOUND_ERROR_CODE, response, "User with user name '" + changePasswordDTO.getUserName() + "' not found");
				return;
			}

			try {
				attachedUser.changePassword(changePasswordDTO.getNewPassword(), changePasswordDTO.getNewPasswordConfirmation());
			} catch (SimbaException ex) {
				sendError(UNABLE_TO_CHANGE_PASSWORD_ERROR_CODE, response, "Something went wrong while changing the password of user : "
						+ attachedUser.getUserName() + ". Message : " + ex.getMessage());
				return;
			}

			userRepository.flush();
		}
	}

	@RequestMapping("createWithRoles")
	@ResponseBody
	public UserDTO create(@JsonBody("user") UserDTO user, @JsonBody("roleNames") List<String> roleNames) {
		User newUser = createUser(user);

		for (String roleName : roleNames) {
			Role role = roleRepository.findByName(roleName);
			if (role == null) {
				throw new IllegalArgumentException("Role name " + roleName + " doesn't exist");
			}
			newUser.addRole(role);
		}

		return UserDTOAssembler.assemble(newUser);
	}

	@RequestMapping("create")
	@ResponseBody
	public UserDTO create(@RequestBody UserDTO user) {
		return UserDTOAssembler.assemble(createUser(user));
	}

	@RequestMapping("createAsClone")
	@ResponseBody
	public UserDTO create(@JsonBody("user") UserDTO user, @JsonBody("userName") String userName) {
		Set<Role> roles = userRepository.findByName(userName).getRoles();

		User newUser = createUser(user);
		newUser.addRoles(roles);

		return UserDTOAssembler.assemble(newUser);
	}

	private User createUser(UserDTO user) {
		DTOValidator.assertValid(user);
		if (userRepository.findByName(user.getUserName()) != null) {
			throw new SimbaException(USER_ALREADY_EXISTS, user.getUserName());
		}

		Boolean passwordChangeRequired = configurationService.getValue(PASSWORD_CHANGE_REQUIRED);
		user.setPasswordChangeRequired(passwordChangeRequired);

		return userRepository.persist(UserAssembler.assemble(user));
	}

	@RequestMapping("update")
	@ResponseBody
	public UserDTO update(@RequestBody UserDTO user) {
		DTOValidator.assertValid(user);
		User attachedUser = userRepository.refreshWithOptimisticLocking(user);
		attachedUser.setFirstName(user.getFirstName());
		attachedUser.setName(user.getName());
		attachedUser.setLanguage(user.getLanguage());
		attachedUser.setChangePasswordOnNextLogon(user.isChangePasswordOnNextLogon());
		attachedUser.setStatus(user.getStatus());
		attachedUser.setSuccessURL(user.getSuccessURL());
		userRepository.flush();
		return UserDTOAssembler.assemble(attachedUser);
	}

	@RequestMapping("refresh")
	@ResponseBody
	public UserDTO refresh(@RequestBody UserDTO user) {
		return UserDTOAssembler.assemble(userRepository.lookUp(user));
	}
}
