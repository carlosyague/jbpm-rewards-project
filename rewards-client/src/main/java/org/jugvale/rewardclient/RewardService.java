package org.jugvale.rewardclient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.audit.AuditService;
import org.kie.api.runtime.manager.audit.ProcessInstanceLog;
import org.kie.api.runtime.manager.audit.VariableInstanceLog;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.remote.client.api.RemoteRuntimeEngineFactory;

/**
 * @author wsiqueir
 *
 */
public class RewardService {

	
	// jBPM Process and Project constants
	private static final String P_EMPLOYEE = "employeeName";
	private static final String P_RESULT = "result";
	private static final String T_APPROVAL_VAR = "_approval";
	private static final String LANG = "en-UK";
	private static final String DEPLOYMENT_ID = "example:rewards-project:1.0";
	private static final String PROCESS_ID = "com.sample.rewards-basic";

	private String username;
					
	// jBPM classes
	private RuntimeEngine engine;
	private KieSession ksession;
	private TaskService taskService;
	private AuditService auditService;

	private static RewardService instance;
	
	public static RewardService getInstance() {
		if (Objects.isNull(instance))
			instance = new RewardService();
		return instance;
	}


	private RewardService() {
		
	}
	
	public void connect(String serverUrl, String username, String password) throws MalformedURLException {
		this.username = username;
		engine = RemoteRuntimeEngineFactory.newRestBuilder()
				.addDeploymentId(DEPLOYMENT_ID).addUserName(username)
				.addPassword(password).addUrl(new URL(serverUrl)).build();
		taskService = engine.getTaskService();
		ksession = engine.getKieSession();
		auditService = engine.getAuditService();
	}

	public List<RewardTask> getTasks() {
		// retrieve all tasks since the USERNAME should be in groups PM and HR
		List<RewardTask> tasks = taskService
				.getTasksAssignedAsPotentialOwner(username, LANG)
				.stream()
				.map(t -> {
					RewardTask rt = new RewardTask();
					rt.setCreated(t.getCreatedOn());
					rt.setName(t.getName());
					rt.setEmployeeName(getEmployeeName(t.getProcessInstanceId()));
					rt.setTaskId(t.getId());
					return rt;
				}).collect(Collectors.toList());
		return tasks;
	}

	public void doTask(long taskId, boolean approve) {
		Map<String, Object> params = new HashMap<>();
		params.put(T_APPROVAL_VAR, approve);
		taskService.claim(taskId, username);
		taskService.start(taskId, username);
		taskService.complete(taskId, username, params);
	}

	public void startRewardProcess(String employeeName) {
		Map<String, Object> params = new HashMap<>();
		params.put(P_EMPLOYEE, employeeName);
		ksession.startProcess(PROCESS_ID, params);
	}

	public List<String> getAllProcessesSummary() {
		return auditService.findProcessInstances(PROCESS_ID).stream()
				.map(this::getProcessSummary).collect(Collectors.toList());
	}

	public String getEmployeeName(long piid) {
		return getVariableValue(piid, P_EMPLOYEE);
	}

	public void clearHistory() {
		auditService.clear();
	}	
	
	private String getVariableValue(long piid, String varName) {
		String value = null;
		List<? extends VariableInstanceLog> variables = auditService
				.findVariableInstances(piid, varName);
		if (variables.size() > 0)
			value = variables.get(0).getValue();
		return value;
	}

	private String getProcessSummary(ProcessInstanceLog pi) {
		long piid = pi.getProcessInstanceId();
		String employee = getVariableValue(piid, P_EMPLOYEE);
		String result = getVariableValue(piid, P_RESULT);
		String status = "";
		switch (pi.getStatus()) {
		case ProcessInstance.STATE_ABORTED:
			status = "aborted";
			break;
		case ProcessInstance.STATE_ACTIVE:
			status = "active";
			break;
		case ProcessInstance.STATE_COMPLETED:
			status = "completed";
			break;
		case ProcessInstance.STATE_PENDING:
			status = "pending";
			break;
		case ProcessInstance.STATE_SUSPENDED:
			status = "suspended";
			break;
		default:
			status = "unknown";
			break;
		}
		if (Objects.isNull(result))
			result = "reward still waiting for approval";
		String summary = "Reward process for employee '%s' is %s and result is '%s'.";
		return String.format(summary, employee, status, result);
	}
	
	public String getUsername() {
		return username;
	}

}