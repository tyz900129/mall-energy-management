package com.wanyuecity.energy.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.merchant.entity.Merchant;
import com.wanyuecity.energy.merchant.mapper.MerchantMapper;
import com.wanyuecity.energy.platform.entity.PlatformAccountCurrent;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.entity.PlatformDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformAccountCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformDeviceCurrentMapper;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.stalldevice.entity.StallDevice;
import com.wanyuecity.energy.stalldevice.mapper.StallDeviceMapper;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import com.wanyuecity.energy.workflow.entity.Workflow;
import com.wanyuecity.energy.workflow.entity.WorkflowDevice;
import com.wanyuecity.energy.workflow.entity.WorkflowExpectedChange;
import com.wanyuecity.energy.workflow.mapper.*;
import com.wanyuecity.energy.workflow.service.dto.*;
import com.wanyuecity.energy.verification.service.VerificationService;
import com.wanyuecity.energy.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.file.*;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final WorkflowMapper workflowMapper;
    private final WorkflowDeviceMapper workflowDeviceMapper;
    private final WorkflowExpectedChangeMapper expectedChangeMapper;
    private final StallMapper stallMapper;
    private final StallDeviceMapper stallDeviceMapper;
    private final MerchantMapper merchantMapper;
    private final PlatformAccountCurrentMapper accountMapper;
    private final PlatformDeviceCurrentMapper deviceMapper;
    private final PlatformAccountDeviceCurrentMapper relationMapper;
    private final SysUserMapper userMapper;
    private final WorkflowStateMachine stateMachine;
    private final VerificationService verificationService;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;
    @Value("${energy.upload.base-dir}") private String uploadBaseDir;

    private static final Set<String> BILLING_CHANGE_TYPES = Set.of(
            "NEW_MERCHANT", "EXISTING_MERCHANT_ADD_STALL", "MERCHANT_EXIT");

    public BillingChangePrecheckView precheckBillingChange(BillingChangeRequest req) {
        String type = req.getWorkflowType() == null ? "" : req.getWorkflowType().trim().toUpperCase();
        if (!BILLING_CHANGE_TYPES.contains(type)) throw BizException.badRequest("不支持的水电费变更类型");
        BillingChangePrecheckView out = new BillingChangePrecheckView();
        out.setWorkflowType(type); out.setMerchantCode(req.getMerchantCode());

        List<Map<String,Object>> accounts = accountsByMerchantCode(req.getMerchantCode());
        out.setMerchantExists(!accounts.isEmpty());
        if (!accounts.isEmpty()) out.setMerchantName(Objects.toString(accounts.get(0).get("account_name"), null));
        Set<String> merchantAccounts = new LinkedHashSet<>();
        accounts.forEach(a -> merchantAccounts.add(Objects.toString(a.get("account_number"), "")));
        out.setMerchantStalls(findMerchantStalls(merchantAccounts));

        if ("NEW_MERCHANT".equals(type) && out.isMerchantExists()) out.getReasons().add("商户代码已存在，请选择“已有商户增加铺位”");
        if (!"NEW_MERCHANT".equals(type) && !out.isMerchantExists()) out.getReasons().add("未查询到该商户代码对应的启用计费账户");
        if ("MERCHANT_EXIT".equals(type) && req.getStallId() == null) {
            out.getReasons().add(out.getMerchantStalls().isEmpty() ? "该商户当前没有关联铺位" : "请选择要解除绑定的铺位");
            out.setAllowed(false); return out;
        }
        if (!"MERCHANT_EXIT".equals(type) && req.getStallId() == null) {
            out.getReasons().add("请选择要操作的铺位"); out.setAllowed(false); return out;
        }

        Stall stall = stallMapper.selectById(req.getStallId());
        if (stall == null) throw BizException.badRequest("铺位不存在");
        if ("DECOMMISSIONED".equals(stall.getStatus())) out.getReasons().add("铺位已停用");
        if ("MERCHANT_EXIT".equals(type) && out.getMerchantStalls().stream().noneMatch(s -> Objects.equals(s.getStallId(), stall.getId())))
            out.getReasons().add("该铺位不属于当前商户，不能发起撤柜解绑");

        ExitCoverage exitCoverage = "MERCHANT_EXIT".equals(type) ? ExitCoverage.empty() : submittedExitCoverage(stall.getId());
        boolean exitCoverageUsed = false;
        List<StallDevice> bindings = stallDeviceMapper.listActiveByStall(stall.getId());
        if (bindings.isEmpty()) out.getReasons().add("铺位尚未绑定任何设备，无法建立计费变更核对任务");
        for (StallDevice binding : bindings) {
            PlatformDeviceCurrent device = deviceMapper.selectOne(new QueryWrapper<PlatformDeviceCurrent>()
                    .eq("platform_device_id", binding.getPlatformDeviceId()).last("LIMIT 1"));
            List<String> currentAccounts = enabledAccountsForDevice(binding.getPlatformDeviceId());
            BillingChangePrecheckView.DeviceCheck check = toDeviceCheck(binding, device, currentAccounts);
            check.setCurrentAccountNames(enabledAccountNamesForDevice(binding.getPlatformDeviceId()));
            boolean water = "WATER".equalsIgnoreCase(check.getEnergyType());
            if (water) out.setHasWaterMeter(true);
            if (!"MERCHANT_EXIT".equals(type) && water && Boolean.FALSE.equals(req.getUsesWater())) check.setIncluded(false);
            if ("NEW_MERCHANT".equals(type) || "EXISTING_MERCHANT_ADD_STALL".equals(type)) {
                List<String> conflicting = "NEW_MERCHANT".equals(type) ? currentAccounts : currentAccounts.stream().filter(a -> !merchantAccounts.contains(a)).toList();
                Set<String> covered = exitCoverage.accountsByDevice().getOrDefault(binding.getPlatformDeviceId(), Set.of());
                check.setConflict(conflicting.stream().anyMatch(a -> !covered.contains(a)));
                if (!conflicting.isEmpty() && !check.isConflict()) exitCoverageUsed = true;
            } else check.setConflict(currentAccounts.stream().noneMatch(merchantAccounts::contains));
            if (check.isConflict()) {
                String deviceLabel = hasText(check.getDeviceName()) ? check.getDeviceName() : binding.getPlatformDeviceId();
                String accountLabels = check.getCurrentAccountNames().isEmpty() ? String.join(",", currentAccounts) : String.join(",", check.getCurrentAccountNames());
                if ("MERCHANT_EXIT".equals(type)) out.getReasons().add("设备“" + deviceLabel + "”未绑定到该商户的启用计费账户");
                else out.getReasons().add("设备“" + deviceLabel + "”已绑定其他启用计费账户“" + accountLabels + "”，请先解除绑定");
            }
            out.getDevices().add(check);
        }
        if (exitCoverageUsed) out.getNotices().add("该铺位已有已提交的撤柜流程（"+String.join("、",exitCoverage.workflowNumbers())+"），且撤柜设备及原计费账户覆盖当前冲突；本次申请允许继续，无需等待源系统同步完成");
        if (!"MERCHANT_EXIT".equals(type) && out.isHasWaterMeter() && req.getUsesWater() == null) {
            out.setRequiresWaterConfirmation(true); out.getReasons().add("该铺位有水表，请确认新商户是否用水");
        }
        out.setAllowed(out.getReasons().isEmpty());
        return out;
    }

    @Transactional
    public WorkflowView createBillingChange(BillingChangeRequest req, String username) {
        Workflow repeated = findByRequestKey(req.getRequestKey(), username);
        if (repeated != null) return get(repeated.getId());
        validateBillingChangeFields(req);
        BillingChangePrecheckView check = precheckBillingChange(req);
        if (!check.isAllowed()) throw BizException.badRequest(String.join("；", check.getReasons()));
        SysUser user = findUser(username);
        Workflow row = new Workflow();
        row.setRequestKey(req.getRequestKey());
        row.setWorkflowNo(nextWorkflowNo()); row.setWorkflowType(check.getWorkflowType()); row.setMerchantCode(req.getMerchantCode());
        row.setMerchantName("NEW_MERCHANT".equals(check.getWorkflowType()) ? req.getMerchantName().trim() : (check.getMerchantName() == null ? req.getMerchantCode() : check.getMerchantName()));
        if ("EXISTING_MERCHANT_ADD_STALL".equals(check.getWorkflowType())) {
            List<Map<String,Object>> sourceAccounts = accountsByMerchantCode(req.getMerchantCode());
            row.setContactName(null);
            row.setContactPhone(sourceAccounts.stream().map(a -> Objects.toString(a.get("mobile_phone"), ""))
                    .filter(this::hasText).findFirst().orElse(null));
        } else {
            row.setContactName(hasText(req.getContactName()) ? req.getContactName().trim() : null);
            row.setContactPhone(hasText(req.getContactPhone()) ? req.getContactPhone().trim() : null);
        }
        row.setStallId(req.getStallId());
        row.setRequestedBillingStartTime(req.getEffectiveTime()); row.setApplicantId(user == null ? null : user.getId());
        row.setApplicantName(username); row.setStatus("DRAFT"); row.setRemark(req.getRemark());
        row.setExpectedAccountAction("NEW_MERCHANT".equals(check.getWorkflowType()) ? "CREATE_AND_BIND" :
                "EXISTING_MERCHANT_ADD_STALL".equals(check.getWorkflowType()) ? "BIND" : "UNBIND");
        row.setUsesWater(req.getUsesWater());
        row.setExtraData("{\"usesWater\":" + (req.getUsesWater() == null ? "null" : req.getUsesWater()) + "}");
        try { workflowMapper.insert(row); }
        catch (DuplicateKeyException ex) {
            Workflow existing = findByRequestKey(req.getRequestKey(), username);
            if (existing != null) return get(existing.getId());
            throw ex;
        }
        String operation = "MERCHANT_EXIT".equals(check.getWorkflowType()) ? "UNBIND" : "BIND";
        Set<String> merchantAccounts = new LinkedHashSet<>();
        accountsByMerchantCode(req.getMerchantCode()).forEach(a -> merchantAccounts.add(Objects.toString(a.get("account_number"), "")));
        Set<String> selectedDeviceIds = new LinkedHashSet<>();
        for (BillingChangePrecheckView.DeviceCheck device : check.getDevices()) {
            if (!device.isIncluded()) continue;
            selectedDeviceIds.add(device.getPlatformDeviceId());
            String oldAccount = device.getCurrentAccountNumbers().stream().filter(merchantAccounts::contains).findFirst().orElse(null);
            WorkflowDevice wd = new WorkflowDevice(); wd.setWorkflowId(row.getId()); wd.setPlatformDeviceId(device.getPlatformDeviceId());
            wd.setDeviceNameSnapshot(device.getDeviceName()); wd.setEnergyTypeSnapshot(device.getEnergyType()); wd.setCommAddressSnapshot(device.getCommAddress());
            wd.setSerialNumberSnapshot(device.getSerialNumber()); wd.setGatewayNameSnapshot(device.getGatewayName()); wd.setChannelNameSnapshot(device.getChannelName());
            wd.setProductNameSnapshot(device.getProductName()); wd.setMeterPhaseSnapshot(meterPhase(device.getEnergyType(), device.getProductName()));
            wd.setOldAccountNumber("UNBIND".equals(operation) ? oldAccount : null);
            wd.setOperationType(operation); workflowDeviceMapper.insert(wd);
            WorkflowExpectedChange ec = new WorkflowExpectedChange(); ec.setWorkflowId(row.getId());
            ec.setChangeType("UNBIND".equals(operation) ? "DEVICE_UNBIND" : "DEVICE_BIND"); ec.setPlatformDeviceId(device.getPlatformDeviceId());
            ec.setFromAccountNumber("UNBIND".equals(operation) ? oldAccount : null);
            ec.setExpectedEffectiveTime(req.getEffectiveTime()); ec.setVerifyStatus("PENDING"); expectedChangeMapper.insert(ec);
        }
        if ("UNBIND".equals(operation)) {
            boolean disableAccount = false;
            for (String account : merchantAccounts) {
                if (selectedDeviceIds.isEmpty() || remainingDeviceCount(account, selectedDeviceIds) > 0) continue;
                WorkflowExpectedChange ec = new WorkflowExpectedChange(); ec.setWorkflowId(row.getId()); ec.setChangeType("ACCOUNT_DISABLE");
                ec.setFromAccountNumber(account); ec.setExpectedEffectiveTime(req.getEffectiveTime()); ec.setVerifyStatus("PENDING"); expectedChangeMapper.insert(ec);
                disableAccount = true;
            }
            if (disableAccount) { row.setExpectedAccountAction("UNBIND_AND_DISABLE"); workflowMapper.updateById(row); }
        }
        return get(row.getId());
    }

    private void validateBillingChangeFields(BillingChangeRequest req) {
        if (req.getEffectiveTime() == null) throw BizException.badRequest("请选择启用或解除绑定时间");
        if ("NEW_MERCHANT".equalsIgnoreCase(req.getWorkflowType()) && !hasText(req.getMerchantName()))
            throw BizException.badRequest("新商户开户必须填写商户名称");
        if ("NEW_MERCHANT".equalsIgnoreCase(req.getWorkflowType()) && (!hasText(req.getContactName()) || !hasText(req.getContactPhone())))
            throw BizException.badRequest("新商户开户必须填写联系人和联系电话");
    }

    private List<Map<String,Object>> accountsByMerchantCode(String code) {
        return jdbc.queryForList("SELECT p.account_number,p.account_name,p.mobile_phone,p.platform_status FROM platform_account_current p LEFT JOIN platform_account_local_profile l ON l.account_number=p.account_number WHERE COALESCE(NULLIF(l.merchant_code,''),p.merchant_code)=? AND (p.platform_status IS NULL OR UPPER(p.platform_status) NOT IN ('0','DISABLED','INACTIVE')) ORDER BY p.account_number", code);
    }

    private List<String> enabledAccountsForDevice(String deviceId) {
        return jdbc.queryForList("SELECT DISTINCT r.account_number FROM platform_account_device_current r JOIN platform_account_current p ON p.account_number=r.account_number WHERE r.platform_device_id=? AND (p.platform_status IS NULL OR UPPER(p.platform_status) NOT IN ('0','DISABLED','INACTIVE')) ORDER BY r.account_number", String.class, deviceId);
    }

    private List<String> enabledAccountNamesForDevice(String deviceId) {
        return jdbc.queryForList("SELECT DISTINCT COALESCE(NULLIF(p.account_name,''),r.account_number) account_label FROM platform_account_device_current r JOIN platform_account_current p ON p.account_number=r.account_number WHERE r.platform_device_id=? AND (p.platform_status IS NULL OR UPPER(p.platform_status) NOT IN ('0','DISABLED','INACTIVE')) ORDER BY account_label", String.class, deviceId);
    }

    private int remainingDeviceCount(String accountNumber, Set<String> excludedDeviceIds) {
        String marks = String.join(",", Collections.nCopies(excludedDeviceIds.size(), "?"));
        List<Object> args = new ArrayList<>(); args.add(accountNumber); args.addAll(excludedDeviceIds);
        Integer count = jdbc.queryForObject("SELECT COUNT(DISTINCT platform_device_id) FROM platform_account_device_current WHERE account_number=? AND platform_device_id NOT IN ("+marks+")", Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    private ExitCoverage submittedExitCoverage(Long stallId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT w.workflow_no,wd.platform_device_id,wd.old_account_number FROM workflow w JOIN workflow_device wd ON wd.workflow_id=w.id WHERE w.stall_id=? AND w.workflow_type='MERCHANT_EXIT' AND w.submitted_at IS NOT NULL AND w.status NOT IN ('DRAFT','CANCELLED') AND wd.operation_type='UNBIND' AND wd.old_account_number IS NOT NULL ORDER BY w.id DESC",stallId);
        Map<String,Set<String>> accounts=new HashMap<>();LinkedHashSet<String> numbers=new LinkedHashSet<>();
        for(Map<String,Object> r:rows){String device=Objects.toString(r.get("platform_device_id"),"");String account=Objects.toString(r.get("old_account_number"),"");if(!device.isBlank()&&!account.isBlank())accounts.computeIfAbsent(device,k->new LinkedHashSet<>()).add(account);String no=Objects.toString(r.get("workflow_no"),"");if(!no.isBlank())numbers.add(no);}
        return new ExitCoverage(accounts,numbers);
    }
    private record ExitCoverage(Map<String,Set<String>> accountsByDevice,Set<String> workflowNumbers){static ExitCoverage empty(){return new ExitCoverage(Map.of(),Set.of());}}

    private List<BillingChangePrecheckView.MerchantStall> findMerchantStalls(Set<String> accountNumbers) {
        if (accountNumbers.isEmpty()) return new ArrayList<>();
        String marks = String.join(",", Collections.nCopies(accountNumbers.size(), "?"));
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT DISTINCT s.id,s.stall_code,s.stall_name,sd.platform_device_id FROM stall s JOIN stall_device sd ON sd.stall_id=s.id AND sd.status='ACTIVE' JOIN platform_account_device_current r ON r.platform_device_id=sd.platform_device_id WHERE r.account_number IN ("+marks+") ORDER BY s.stall_code,sd.platform_device_id", accountNumbers.toArray());
        Map<Long,BillingChangePrecheckView.MerchantStall> grouped = new LinkedHashMap<>();
        for (Map<String,Object> r : rows) {
            Long id = ((Number)r.get("id")).longValue();
            BillingChangePrecheckView.MerchantStall s = grouped.computeIfAbsent(id, k -> { BillingChangePrecheckView.MerchantStall x=new BillingChangePrecheckView.MerchantStall(); x.setStallId(k); x.setStallCode(Objects.toString(r.get("stall_code"),"")); x.setStallName(Objects.toString(r.get("stall_name"),"")); return x; });
            s.getDeviceIds().add(Objects.toString(r.get("platform_device_id"),""));
        }
        return new ArrayList<>(grouped.values());
    }

    private BillingChangePrecheckView.DeviceCheck toDeviceCheck(StallDevice binding, PlatformDeviceCurrent device, List<String> accounts) {
        BillingChangePrecheckView.DeviceCheck c = new BillingChangePrecheckView.DeviceCheck(); c.setPlatformDeviceId(binding.getPlatformDeviceId()); c.setCurrentAccountNumbers(accounts);
        if (device != null) { c.setDeviceName(device.getDeviceName()); c.setEnergyType(device.getEnergyType()); c.setCommAddress(device.getCommAddress()); c.setSerialNumber(device.getSerialNumber()); c.setGatewayName(device.getGatewayName()); c.setChannelName(device.getChannelName()); c.setProductName(device.getProductName()); }
        else c.setEnergyType(binding.getEnergyType());
        return c;
    }

    private String meterPhase(String energyType, String productName) {
        if (!"ELECTRIC".equalsIgnoreCase(energyType)) return null;
        String name = productName == null ? "" : productName.toUpperCase();
        if (name.contains("三相") || name.contains("三线") || name.contains("四线") || name.contains("3P")) return "THREE_PHASE";
        return "SINGLE_PHASE";
    }

    public NewMerchantPrecheckView precheckNewMerchant(NewMerchantPrecheckRequest req) {
        Stall stall = stallMapper.selectById(req.getStallId());
        if (stall == null) throw BizException.badRequest("stall not found: id=" + req.getStallId());
        NewMerchantPrecheckView out = new NewMerchantPrecheckView();
        out.setMerchantCode(req.getMerchantCode()); out.setStallId(stall.getId()); out.setStallCode(stall.getStallCode());
        boolean localExists = merchantMapper.selectCount(new QueryWrapper<Merchant>().eq("merchant_code", req.getMerchantCode())) > 0;
        boolean platformExists = accountMapper.selectCount(new QueryWrapper<PlatformAccountCurrent>().eq("merchant_code", req.getMerchantCode())) > 0;
        out.setMerchantCodeExists(localExists || platformExists);
        if (out.isMerchantCodeExists()) out.getReasons().add("商户代码已存在，不能创建全新商户流程");
        if ("DECOMMISSIONED".equals(stall.getStatus())) out.getReasons().add("铺位已停用");

        List<StallDevice> bindings = stallDeviceMapper.listActiveByStall(stall.getId());
        if (bindings.isEmpty()) out.getReasons().add("铺位尚未绑定任何设备");
        for (StallDevice binding : bindings) {
            PlatformDeviceCurrent device = deviceMapper.selectOne(new QueryWrapper<PlatformDeviceCurrent>()
                    .eq("platform_device_id", binding.getPlatformDeviceId()).last("LIMIT 1"));
            List<PlatformAccountDeviceCurrent> relations = relationMapper.selectList(new QueryWrapper<PlatformAccountDeviceCurrent>()
                    .eq("platform_device_id", binding.getPlatformDeviceId()));
            NewMerchantPrecheckView.DeviceCheck check = new NewMerchantPrecheckView.DeviceCheck();
            check.setPlatformDeviceId(binding.getPlatformDeviceId());
            if (device != null) { check.setDeviceName(device.getDeviceName()); check.setEnergyType(device.getEnergyType());
                check.setCommAddress(device.getCommAddress()); check.setSerialNumber(device.getSerialNumber()); }
            check.setCurrentAccountNumbers(relations.stream().map(PlatformAccountDeviceCurrent::getAccountNumber).distinct().toList());
            check.setConflict(!check.getCurrentAccountNumbers().isEmpty());
            if (check.isConflict()) out.getReasons().add("设备 " + binding.getPlatformDeviceId() + " 仍绑定计费账户 " + String.join(",", check.getCurrentAccountNumbers()));
            out.getDevices().add(check);
        }
        out.setAllowed(out.getReasons().isEmpty());
        return out;
    }

    @Transactional
    public WorkflowView createNewMerchant(NewMerchantWorkflowRequest req, String username) {
        Workflow repeated = findByRequestKey(req.getRequestKey(), username);
        if (repeated != null) return get(repeated.getId());
        NewMerchantPrecheckRequest checkReq = new NewMerchantPrecheckRequest();
        checkReq.setMerchantCode(req.getMerchantCode()); checkReq.setStallId(req.getStallId());
        NewMerchantPrecheckView check = precheckNewMerchant(checkReq);
        if (!check.isAllowed()) throw BizException.badRequest(String.join("；", check.getReasons()));
        SysUser user = findUser(username);
        Workflow row = new Workflow();
        row.setRequestKey(req.getRequestKey());
        row.setWorkflowNo(nextWorkflowNo()); row.setWorkflowType("NEW_MERCHANT"); row.setMerchantCode(req.getMerchantCode());
        row.setMerchantName(req.getMerchantName()); row.setContactName(req.getContactName()); row.setContactPhone(req.getContactPhone());
        row.setStallId(req.getStallId()); row.setRequestedBillingStartTime(req.getRequestedBillingStartTime());
        row.setApplicantId(user == null ? null : user.getId()); row.setApplicantName(username); row.setStatus("DRAFT");
        row.setRemark(req.getRemark()); row.setExtraData("{\"targetAccountResolution\":\"MERCHANT_CODE_AFTER_PLATFORM_CREATION\"}");
        try { workflowMapper.insert(row); }
        catch (DuplicateKeyException ex) {
            Workflow existing = findByRequestKey(req.getRequestKey(), username);
            if (existing != null) return get(existing.getId());
            throw ex;
        }
        for (NewMerchantPrecheckView.DeviceCheck device : check.getDevices()) {
            WorkflowDevice wd = new WorkflowDevice(); wd.setWorkflowId(row.getId()); wd.setPlatformDeviceId(device.getPlatformDeviceId());
            wd.setDeviceNameSnapshot(device.getDeviceName()); wd.setEnergyTypeSnapshot(device.getEnergyType());
            wd.setCommAddressSnapshot(device.getCommAddress()); wd.setSerialNumberSnapshot(device.getSerialNumber()); wd.setOperationType("BIND");
            workflowDeviceMapper.insert(wd);
            WorkflowExpectedChange ec = new WorkflowExpectedChange(); ec.setWorkflowId(row.getId()); ec.setChangeType("DEVICE_BIND");
            ec.setPlatformDeviceId(device.getPlatformDeviceId()); ec.setExpectedEffectiveTime(req.getRequestedBillingStartTime()); ec.setVerifyStatus("PENDING");
            expectedChangeMapper.insert(ec);
        }
        return get(row.getId());
    }

    private Workflow findByRequestKey(String requestKey, String username) {
        if (!hasText(requestKey)) return null;
        Workflow existing = workflowMapper.selectOne(new QueryWrapper<Workflow>()
                .eq("request_key", requestKey).last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getApplicantName(), username))
            throw BizException.badRequest("流程请求编号已被使用");
        return existing;
    }

    @Transactional
    public WorkflowView submit(Long id) {
        Workflow row = require(id); stateMachine.requireTransition(row.getStatus(), "FIELD_CHECK_PENDING");
        if (BILLING_CHANGE_TYPES.contains(row.getWorkflowType())) {
            BillingChangeRequest req = new BillingChangeRequest(); req.setWorkflowType(row.getWorkflowType()); req.setMerchantCode(row.getMerchantCode()); req.setStallId(row.getStallId());
            req.setEffectiveTime(row.getRequestedBillingStartTime()); req.setUsesWater(row.getUsesWater()!=null?row.getUsesWater():row.getExtraData()!=null&&row.getExtraData().replace(" ","").contains("\"usesWater\":true") ? Boolean.TRUE : row.getExtraData()!=null&&row.getExtraData().replace(" ","").contains("\"usesWater\":false") ? Boolean.FALSE : null);
            BillingChangePrecheckView check = precheckBillingChange(req);
            if (!check.isAllowed()) throw BizException.badRequest(String.join("；", check.getReasons()));
        }
        row.setStatus("FIELD_CHECK_PENDING"); row.setSubmittedAt(LocalDateTime.now()); workflowMapper.updateById(row);
        verificationService.createForWorkflow(row.getId());
        notificationService.notifyVerificationCreated(row.getId());
        return get(id);
    }

    @Transactional
    public WorkflowView cancel(Long id) {
        Workflow row = require(id); stateMachine.requireTransition(row.getStatus(), "CANCELLED");
        row.setStatus("CANCELLED"); workflowMapper.updateById(row); return get(id);
    }

    /** 超级管理员复位：保留申请和设备快照，清空核验、照片、匹配及推送记录，回到草稿。 */
    @Transactional
    public WorkflowView reset(Long id) {
        Workflow row = require(id);
        clearRuntimeRecords(id);
        jdbc.update("UPDATE workflow_expected_change SET actual_account_number=NULL,verify_status='PENDING',verified_at=NULL,exception_reason=NULL WHERE workflow_id=?", id);
        jdbc.update("UPDATE workflow SET status='DRAFT',submitted_at=NULL,field_verified_at=NULL,waiting_platform_at=NULL,verified_at=NULL,completed_at=NULL,oa_process_no=NULL,completion_remark=NULL WHERE id=?", id);
        return get(id);
    }

    /** 超级管理员永久删除：清除流程及全部从属业务记录和核验附件。 */
    @Transactional
    public void permanentDelete(Long id) {
        require(id);
        clearRuntimeRecords(id);
        jdbc.update("DELETE FROM workflow_expected_change WHERE workflow_id=?", id);
        jdbc.update("DELETE FROM workflow_device WHERE workflow_id=?", id);
        jdbc.update("DELETE FROM data_exception WHERE workflow_id=?", id);
        jdbc.update("DELETE FROM webhook_log WHERE workflow_id=?", id);
        jdbc.update("DELETE FROM workflow_match_resolution WHERE workflow_id=?", id);
        jdbc.update("DELETE FROM workflow WHERE id=?", id);
    }

    private void clearRuntimeRecords(Long workflowId) {
        List<Long> taskIds = jdbc.queryForList("SELECT id FROM field_verification_task WHERE workflow_id=?", Long.class, workflowId);
        if (!taskIds.isEmpty()) {
            String taskMarks = String.join(",", Collections.nCopies(taskIds.size(), "?"));
            List<Long> deviceIds = jdbc.queryForList("SELECT id FROM field_verification_device WHERE verification_task_id IN ("+taskMarks+")", Long.class, taskIds.toArray());
            if (!deviceIds.isEmpty()) {
                String deviceMarks = String.join(",", Collections.nCopies(deviceIds.size(), "?"));
                List<String> paths = jdbc.queryForList("SELECT storage_path FROM attachment WHERE business_type='VERIFICATION_DEVICE' AND business_id IN ("+deviceMarks+")", String.class, deviceIds.toArray());
                jdbc.update("DELETE FROM attachment WHERE business_type='VERIFICATION_DEVICE' AND business_id IN ("+deviceMarks+")", deviceIds.toArray());
                jdbc.update("DELETE FROM field_verification_device WHERE id IN ("+deviceMarks+")", deviceIds.toArray());
                deleteStoredFiles(paths);
            }
            jdbc.update("DELETE FROM field_verification_task WHERE id IN ("+taskMarks+")", taskIds.toArray());
        }
        List<Long> runIds = jdbc.queryForList("SELECT id FROM workflow_match_run WHERE workflow_id=?", Long.class, workflowId);
        if (!runIds.isEmpty()) {
            String marks = String.join(",", Collections.nCopies(runIds.size(), "?"));
            jdbc.update("DELETE FROM workflow_match_detail WHERE match_run_id IN ("+marks+")", runIds.toArray());
        }
        jdbc.update("DELETE FROM workflow_match_run WHERE workflow_id=?", workflowId);
        jdbc.update("DELETE FROM workflow_match_resolution WHERE workflow_id=?", workflowId);
        jdbc.update("DELETE FROM webhook_log WHERE workflow_id=?", workflowId);
        jdbc.update("DELETE FROM data_exception WHERE workflow_id=?", workflowId);
    }

    private void deleteStoredFiles(List<String> paths) {
        if (uploadBaseDir == null) return;
        Path root = Path.of(uploadBaseDir).toAbsolutePath().normalize();
        for (String stored : paths) try {
            Path file = root.resolve(stored).normalize();
            if (file.startsWith(root)) Files.deleteIfExists(file);
        } catch (Exception ignored) { }
    }

    public Page<WorkflowView> list(String status, String workflowType, String keyword, String applicantScope, int pageNum, int pageSize) {
        QueryWrapper<Workflow> q = new QueryWrapper<>();
        if (hasText(applicantScope)) q.eq("applicant_name", applicantScope);
        if (hasText(status)) q.eq("status", status.trim()); if (hasText(workflowType)) q.eq("workflow_type", workflowType.trim());
        if (hasText(keyword)) { String k=keyword.trim(); q.and(w -> w.like("workflow_no",k).or().like("merchant_code",k).or().like("merchant_name",k)); }
        q.orderByDesc("created_at"); Page<Workflow> p=workflowMapper.selectPage(Page.of(pageNum, Math.min(Math.max(pageSize,1),100)),q);
        Page<WorkflowView> out=Page.of(p.getCurrent(),p.getSize(),p.getTotal()); out.setRecords(p.getRecords().stream().map(this::toView).toList()); return out;
    }

    public WorkflowView get(Long id) { return toView(require(id)); }
    public WorkflowView getAccessible(Long id,String username,boolean ownOnly){Workflow w=require(id);requireOwner(w,username,ownOnly);return toView(w);}
    @Transactional public WorkflowView submitAccessible(Long id,String username,boolean ownOnly){requireOwner(require(id),username,ownOnly);return submit(id);}
    @Transactional public WorkflowView cancelAccessible(Long id,String username,boolean ownOnly){requireOwner(require(id),username,ownOnly);return cancel(id);}
    public List<WorkflowDevice> devicesAccessible(Long id,String username,boolean ownOnly){requireOwner(require(id),username,ownOnly);return devices(id);}
    public List<WorkflowExpectedChange> changesAccessible(Long id,String username,boolean ownOnly){requireOwner(require(id),username,ownOnly);return expectedChanges(id);}
    private void requireOwner(Workflow w,String username,boolean ownOnly){if(ownOnly&&!Objects.equals(w.getApplicantName(),username))throw BizException.forbidden("applicants may only access their own workflows");}
    public List<WorkflowDevice> devices(Long id) { require(id); return workflowDeviceMapper.selectList(new QueryWrapper<WorkflowDevice>().eq("workflow_id",id).orderByAsc("id")); }
    public List<WorkflowExpectedChange> expectedChanges(Long id) { require(id); return expectedChangeMapper.selectList(new QueryWrapper<WorkflowExpectedChange>().eq("workflow_id",id).orderByAsc("id")); }
    public Map<String,Object> matchEvidence(Long id){require(id);List<Map<String,Object>> runs=jdbc.queryForList("SELECT id,sync_batch_id,match_status,matched_count,total_count,summary,created_at FROM workflow_match_run WHERE workflow_id=? ORDER BY id DESC",id);for(Map<String,Object> r:runs)r.put("details",jdbc.queryForList("SELECT platform_device_id,expected_account_number,actual_account_numbers,match_status,reason FROM workflow_match_detail WHERE match_run_id=? ORDER BY id",r.get("id")));return Map.of("runs",runs,"resolutions",jdbc.queryForList("SELECT action,from_status,to_status,conclusion,operator_name,created_at FROM workflow_match_resolution WHERE workflow_id=? ORDER BY id DESC",id));}
    @Transactional public WorkflowView resolveMatch(Long id,MatchResolutionRequest req,String operator){Workflow w=require(id);if(!Set.of("PARTIAL_MATCH","EXCEPTION").contains(w.getStatus()))throw BizException.badRequest("workflow is not awaiting exception resolution");String action=req.getAction().trim().toUpperCase(),from=w.getStatus(),to;if("RETRY".equals(action))to="WAITING_PLATFORM_CHANGE";else if("CLOSE_EXCEPTION".equals(action)){if(!hasText(req.getConclusion()))throw BizException.badRequest("conclusion is required");to="RESOLVED_EXCEPTION";w.setCompletedAt(LocalDateTime.now());}else throw BizException.badRequest("invalid resolution action");w.setStatus(to);workflowMapper.updateById(w);jdbc.update("INSERT INTO workflow_match_resolution(workflow_id,action,from_status,to_status,conclusion,operator_name) VALUES(?,?,?,?,?,?)",id,action,from,to,hasText(req.getConclusion())?req.getConclusion().trim():null,operator==null?"system":operator);return get(id);}
    @Transactional public WorkflowView updateOaProcessNo(Long id,String value){Workflow w=requirePlatformInfoEditable(id);w.setOaProcessNo(trimToNull(value,100));workflowMapper.updateById(w);return get(id);}
    @Transactional public WorkflowView updateCompletionRemark(Long id,String value){Workflow w=requirePlatformInfoEditable(id);w.setCompletionRemark(trimToNull(value,1000));workflowMapper.updateById(w);return get(id);}
    private Workflow requirePlatformInfoEditable(Long id){Workflow w=require(id);if(!Set.of("WAITING_PLATFORM_CHANGE","PLATFORM_VERIFYING","PARTIAL_MATCH","EXCEPTION","VERIFIED","COMPLETED","RESOLVED_EXCEPTION").contains(w.getStatus()))throw BizException.badRequest("仅平台处理阶段或已完成流程可以维护 OA 信息");return w;}
    private String trimToNull(String value,int max){if(value==null||value.isBlank())return null;String v=value.trim();if(v.length()>max)throw BizException.badRequest("填写内容不能超过"+max+"个字符");return v;}
    private Workflow require(Long id) { Workflow row=workflowMapper.selectById(id); if(row==null) throw BizException.notFound("workflow not found: id="+id); return row; }
    private WorkflowView toView(Workflow row) { WorkflowView v=new WorkflowView();
        v.setId(row.getId());v.setWorkflowNo(row.getWorkflowNo());v.setWorkflowType(row.getWorkflowType());v.setMerchantCode(row.getMerchantCode());v.setMerchantName(row.getMerchantName());v.setContactName(row.getContactName());v.setContactPhone(row.getContactPhone());v.setStallId(row.getStallId());
        Stall s=stallMapper.selectById(row.getStallId()); if(s!=null){v.setStallCode(s.getStallCode());v.setStallName(s.getStallName());v.setFloorCode(s.getFloorCode());v.setFloorName(s.getFloorName());v.setStallBusinessType(s.getBusinessType());v.setStallBusinessCategory(s.getBusinessCategory());}
        List<WorkflowDevice> workflowDevices=devices(row.getId());
        String accountNumber="MERCHANT_EXIT".equals(row.getWorkflowType())?workflowDevices.stream().map(WorkflowDevice::getOldAccountNumber).filter(this::hasText).findFirst().orElse(null):row.getTargetAccountNumber();
        PlatformAccountCurrent billingAccount=null;
        if(hasText(accountNumber))billingAccount=accountMapper.selectOne(new QueryWrapper<PlatformAccountCurrent>().eq("account_number",accountNumber).last("LIMIT 1"));
        if(billingAccount==null&&hasText(row.getMerchantCode()))billingAccount=accountMapper.selectOne(new QueryWrapper<PlatformAccountCurrent>().eq("merchant_code",row.getMerchantCode()).orderByDesc("id").last("LIMIT 1"));
        v.setBillingAccountName(billingAccount==null?null:billingAccount.getAccountName());
        v.setTargetAccountNumber(row.getTargetAccountNumber());v.setRequestedBillingStartTime(row.getRequestedBillingStartTime());v.setApplicantName(row.getApplicantName());v.setStatus(row.getStatus());v.setSubmittedAt(row.getSubmittedAt());v.setExpectedAccountAction(row.getExpectedAccountAction());v.setRemark(row.getRemark());v.setOaProcessNo(row.getOaProcessNo());v.setCompletionRemark(row.getCompletionRemark());v.setCreatedAt(row.getCreatedAt());v.setUpdatedAt(row.getUpdatedAt());v.setDevices(workflowDevices);v.setExpectedChanges(expectedChanges(row.getId()));return v; }
    private SysUser findUser(String username){if(!hasText(username))return null;return userMapper.selectOne(new QueryWrapper<SysUser>().eq("username",username).last("LIMIT 1"));}
    private String nextWorkflowNo(){return "WF"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))+UUID.randomUUID().toString().substring(0,4).toUpperCase();}
    private boolean hasText(String s){return s!=null&&!s.isBlank();}
}
