package com.wanyuecity.energy.system.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.AirConditionerSpecification;
import com.wanyuecity.energy.system.entity.BusinessDictionary;
import com.wanyuecity.energy.system.entity.CableSpecification;
import com.wanyuecity.energy.system.entity.PowerSwitchSpecification;
import com.wanyuecity.energy.system.service.dto.AirConditionerSpecificationRequest;
import com.wanyuecity.energy.system.service.dto.BusinessDictionaryRequest;
import com.wanyuecity.energy.system.service.dto.CableSpecificationRequest;
import com.wanyuecity.energy.system.service.dto.DictionaryImportResult;
import com.wanyuecity.energy.system.service.dto.PowerSwitchSpecificationRequest;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictionaryExcelService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 2_000;
    private static final String CABLE = "CABLE";
    private static final String SWITCH = "SWITCH";
    private static final String AIR_CONDITIONER = "AIR_CONDITIONER_SPEC";

    private static final Map<String, String> TYPE_LABELS = Map.ofEntries(
        Map.entry("FLOOR", "楼层"), Map.entry("BUSINESS_TYPE", "业态"),
        Map.entry("METER_LOCATION", "电表位置"), Map.entry("WATER_METER_CALIBER", "水表口径"),
        Map.entry("DRAINAGE_PIPE_CALIBER", "排水管口径"), Map.entry("EXHAUST_FAN", "排油烟风机"),
        Map.entry("FRESH_AIR_UNIT", "补风机"), Map.entry(AIR_CONDITIONER, "空调设备规格"),
        Map.entry("NATURAL_GAS_CALIBER", "天然气管径"), Map.entry("GAS_SHUTOFF_LOCATION", "燃气切断阀位置"),
        Map.entry("POWER_NODE_TYPE", "配电节点类型"), Map.entry("WATER_SUPPLY_NODE_TYPE", "供水节点类型"),
        Map.entry("WATER_SUPPLY_PIPE_SPEC", "供水管道规格"), Map.entry("DRAINAGE_NODE_TYPE", "排水节点类型"),
        Map.entry("DRAINAGE_MEDIUM", "排水性质"), Map.entry("OIL_EXHAUST_NODE_TYPE", "排油烟节点类型"),
        Map.entry("FRESH_AIR_NODE_TYPE", "补风节点类型"), Map.entry("AIR_DUCT_SPEC", "排烟补风管规格"),
        Map.entry(CABLE, "电缆规格"), Map.entry(SWITCH, "供电开关规格")
    );

    private final BusinessDictionaryService dictionaryService;
    private final CableSpecificationService cableService;
    private final PowerSwitchSpecificationService switchService;
    private final AirConditionerSpecificationService airConditionerService;

    public byte[] export(String requestedType, boolean template) {
        String type = validateType(requestedType);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet("数据");
            List<Column> columns = columns(type);
            CellStyle headerStyle = headerStyle(workbook);
            Row header = data.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i, CellType.STRING);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(headerStyle);
                data.setColumnWidth(i, columns.get(i).width() * 256);
            }
            data.createFreezePane(0, 1);
            data.setAutoFilter(new CellRangeAddress(0, 0, 0, columns.size() - 1));
            if (!template) writeRows(type, data);
            else data.createRow(1);
            writeInstructions(workbook, type, columns);
            Sheet metadata = workbook.createSheet("_meta");
            metadata.createRow(0).createCell(0).setCellValue(type);
            workbook.setSheetHidden(workbook.getSheetIndex(metadata), true);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成字典表格失败", ex);
        }
    }

    @Transactional
    public DictionaryImportResult importWorkbook(String requestedType, MultipartFile file) {
        String type = validateType(requestedType);
        if (file == null || file.isEmpty()) throw BizException.badRequest("请选择需要导入的 Excel 文件");
        if (file.getSize() > MAX_FILE_SIZE) throw BizException.badRequest("导入文件不能超过 5MB");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            verifyMetadata(workbook, type);
            Sheet sheet = workbook.getSheetAt(0);
            List<Column> columns = columns(type);
            validateHeaders(sheet, columns);
            List<RowValues> values = readRows(sheet, columns);
            if (values.isEmpty()) throw BizException.badRequest("导入文件没有可处理的数据");
            List<String> errors = validateRows(type, values);
            if (!errors.isEmpty()) throw BizException.badRequest(errorMessage(errors));
            return saveRows(type, values);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.badRequest("无法读取 Excel 文件，请使用本页面下载的模板并确认文件未损坏");
        }
    }

    public String fileName(String requestedType, boolean template) {
        String type = validateType(requestedType);
        return TYPE_LABELS.getOrDefault(type, type) + (template ? "_导入模板.xlsx" : "_字典数据.xlsx");
    }

    private String validateType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(CABLE, SWITCH, AIR_CONDITIONER).contains(type) && !BusinessDictionaryService.TYPES.contains(type)) {
            throw BizException.badRequest("不支持的字典分类");
        }
        return type;
    }

    private List<Column> columns(String type) {
        if (CABLE.equals(type)) return List.of(
            col("规格编码*", 22), col("电缆型号*", 18), col("规格*", 26), col("单根额定电流(A)*", 20),
            col("单根承载功率(kW)*", 22), col("排序", 10), col("状态", 12), col("备注", 32));
        if (SWITCH.equals(type)) return List.of(
            col("规格编码*", 22), col("品牌", 16), col("开关型号*", 24), col("开关类型", 16),
            col("极数*", 12), col("额定电流(A)*", 18), col("分断能力(kA)", 18), col("排序", 10),
            col("状态", 12), col("备注", 32));
        if (AIR_CONDITIONER.equals(type)) return List.of(
            col("规格编码*", 22), col("空调型号*", 24), col("空调类型*", 18), col("额定功率(kW)*", 18),
            col("额定出风量(m³/h)*", 22), col("排序", 10), col("状态", 12), col("备注", 32));
        return List.of(col("取值编码*", 22), col("显示名称*", 24), col("排序", 10), col("状态", 12), col("备注", 32));
    }

    private Column col(String header, int width) { return new Column(header, width); }

    private void writeRows(String type, Sheet sheet) {
        int rowIndex = 1;
        if (CABLE.equals(type)) {
            for (CableSpecification row : cableService.list(false)) write(sheet.createRow(rowIndex++),
                row.getSpecCode(), row.getCableModel(), row.getSpecification(), row.getRatedCurrentA(), row.getRatedPowerKw(),
                row.getSortOrder(), statusLabel(row.getStatus()), row.getRemark());
        } else if (SWITCH.equals(type)) {
            for (PowerSwitchSpecification row : switchService.list(false)) write(sheet.createRow(rowIndex++),
                row.getSpecCode(), row.getBrand(), row.getSwitchModel(), row.getSwitchType(), row.getPoleCount(),
                row.getRatedCurrentA(), row.getBreakingCapacityKa(), row.getSortOrder(), statusLabel(row.getStatus()), row.getRemark());
        } else if (AIR_CONDITIONER.equals(type)) {
            for (AirConditionerSpecification row : airConditionerService.list(false)) write(sheet.createRow(rowIndex++),
                row.getSpecCode(), row.getModel(), equipmentTypeLabel(row.getEquipmentType()), row.getRatedPowerKw(), row.getAirflowCmh(),
                row.getSortOrder(), statusLabel(row.getStatus()), row.getRemark());
        } else {
            for (BusinessDictionary row : dictionaryService.list(type, false)) write(sheet.createRow(rowIndex++),
                row.getItemValue(), row.getItemLabel(), row.getSortOrder(), statusLabel(row.getStatus()), row.getRemark());
        }
    }

    private void write(Row row, Object... values) {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            Cell cell = row.createCell(i);
            if (value instanceof Number number) cell.setCellValue(number.doubleValue());
            else cell.setCellValue(value == null ? "" : String.valueOf(value));
        }
    }

    private void writeInstructions(Workbook workbook, String type, List<Column> columns) {
        Sheet sheet = workbook.createSheet("填写说明");
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 86 * 256);
        CellStyle header = headerStyle(workbook);
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("项目"); title.getCell(0).setCellStyle(header);
        title.createCell(1).setCellValue("说明"); title.getCell(1).setCellStyle(header);
        instruction(sheet, 1, "当前分类", TYPE_LABELS.getOrDefault(type, type));
        instruction(sheet, 2, "导入规则", "以编码为唯一标识：编码已存在时更新，编码不存在时新增；不会删除文件中未出现的数据。");
        instruction(sheet, 3, "完整性", "带 * 的字段必填。任何一行校验失败时，整批数据均不会写入。");
        instruction(sheet, 4, "状态", "填写“启用”或“停用”，也支持 1 或 0；留空默认为启用。");
        instruction(sheet, 5, "排序", "填写大于或等于 0 的整数；留空默认为 0。");
        if (SWITCH.equals(type)) instruction(sheet, 6, "开关类型", "可填写 MCB、MCCB、RCBO、ISOLATOR、OTHER，或对应中文名称。");
        if (AIR_CONDITIONER.equals(type)) instruction(sheet, 6, "空调类型", "可填写“风机盘管”“吊柜式空调”“其他”，也支持 FAN_COIL、HANGING_CABINET、OTHER。");
        int row = Math.max(7, sheet.getLastRowNum() + 2);
        instruction(sheet, row, "数据列", columns.stream().map(Column::header).collect(Collectors.joining("、")));
    }

    private void instruction(Sheet sheet, int index, String name, String description) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(description);
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private void verifyMetadata(Workbook workbook, String type) {
        Sheet metadata = workbook.getSheet("_meta");
        if (metadata == null || metadata.getRow(0) == null || metadata.getRow(0).getCell(0) == null) return;
        String sourceType = new DataFormatter().formatCellValue(metadata.getRow(0).getCell(0)).trim();
        if (!sourceType.isEmpty() && !type.equalsIgnoreCase(sourceType)) {
            throw BizException.badRequest("所选字典分类与 Excel 模板不一致，请切换到正确分类后重新导入");
        }
    }

    private void validateHeaders(Sheet sheet, List<Column> columns) {
        Row header = sheet.getRow(0);
        if (header == null) throw BizException.badRequest("Excel 第一行必须是表头");
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < columns.size(); i++) {
            String actual = formatter.formatCellValue(header.getCell(i)).trim();
            if (!columns.get(i).header().equals(actual)) {
                throw BizException.badRequest("第 " + (i + 1) + " 列表头应为“" + columns.get(i).header() + "”，请使用本页面下载的模板");
            }
        }
    }

    private List<RowValues> readRows(Sheet sheet, List<Column> columns) {
        if (sheet.getLastRowNum() > MAX_ROWS) throw BizException.badRequest("单次最多导入 " + MAX_ROWS + " 条数据");
        DataFormatter formatter = new DataFormatter(Locale.SIMPLIFIED_CHINESE);
        List<RowValues> result = new ArrayList<>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            Map<String, String> values = new LinkedHashMap<>();
            boolean blank = true;
            for (int column = 0; column < columns.size(); column++) {
                Cell cell = row.getCell(column);
                if (cell != null && cell.getCellType() == CellType.FORMULA) {
                    throw BizException.badRequest("第 " + (index + 1) + " 行不能使用公式，请填写普通值");
                }
                String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                values.put(columns.get(column).header(), value);
                if (!value.isEmpty()) blank = false;
            }
            if (!blank) result.add(new RowValues(index + 1, values));
        }
        return result;
    }

    private List<String> validateRows(String type, List<RowValues> rows) {
        List<String> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (RowValues row : rows) {
            try {
                String code = required(row, codeHeader(type), 64);
                if (!codes.add(code)) errors.add("第 " + row.number() + " 行：编码“" + code + "”在文件中重复");
                if (CABLE.equals(type)) validateCable(row);
                else if (SWITCH.equals(type)) validateSwitch(row);
                else if (AIR_CONDITIONER.equals(type)) validateAirConditioner(row);
                else validateDictionary(row);
            } catch (IllegalArgumentException ex) {
                errors.add("第 " + row.number() + " 行：" + ex.getMessage());
            }
        }
        return errors;
    }

    private void validateDictionary(RowValues row) {
        required(row, "显示名称*", 64); sortOrder(row); status(row); optional(row, "备注", 255);
    }

    private void validateCable(RowValues row) {
        required(row, "电缆型号*", 100); required(row, "规格*", 160);
        positiveDecimal(row, "单根额定电流(A)*", true); positiveDecimal(row, "单根承载功率(kW)*", true);
        sortOrder(row); status(row); optional(row, "备注", 500);
    }

    private void validateSwitch(RowValues row) {
        optional(row, "品牌", 100); required(row, "开关型号*", 120); switchType(row);
        String pole = required(row, "极数*", 20).toUpperCase(Locale.ROOT);
        if (!Set.of("1P", "2P", "3P", "4P").contains(pole)) throw new IllegalArgumentException("极数只能填写 1P、2P、3P 或 4P");
        positiveDecimal(row, "额定电流(A)*", true); positiveDecimal(row, "分断能力(kA)", false);
        sortOrder(row); status(row); optional(row, "备注", 500);
    }

    private void validateAirConditioner(RowValues row) {
        required(row, "空调型号*", 128); equipmentType(row);
        positiveDecimal(row, "额定功率(kW)*", true);
        Integer airflow = integer(row, "额定出风量(m³/h)*", null);
        if (airflow == null || airflow < 1) throw new IllegalArgumentException("额定出风量必须是大于 0 的整数");
        sortOrder(row); status(row); optional(row, "备注", 500);
    }

    private DictionaryImportResult saveRows(String type, List<RowValues> rows) {
        int created = 0, updated = 0;
        if (CABLE.equals(type)) {
            Map<String, CableSpecification> existing = cableService.list(false).stream().collect(Collectors.toMap(CableSpecification::getSpecCode, Function.identity()));
            for (RowValues row : rows) {
                CableSpecificationRequest request = cableRequest(row); CableSpecification current = existing.get(request.getSpecCode());
                if (current == null) { cableService.create(request); created++; } else { cableService.update(current.getId(), request); updated++; }
            }
        } else if (SWITCH.equals(type)) {
            Map<String, PowerSwitchSpecification> existing = switchService.list(false).stream().collect(Collectors.toMap(PowerSwitchSpecification::getSpecCode, Function.identity()));
            for (RowValues row : rows) {
                PowerSwitchSpecificationRequest request = switchRequest(row); PowerSwitchSpecification current = existing.get(request.getSpecCode());
                if (current == null) { switchService.create(request); created++; } else { switchService.update(current.getId(), request); updated++; }
            }
        } else if (AIR_CONDITIONER.equals(type)) {
            Map<String, AirConditionerSpecification> existing = airConditionerService.list(false).stream().collect(Collectors.toMap(AirConditionerSpecification::getSpecCode, Function.identity()));
            for (RowValues row : rows) {
                AirConditionerSpecificationRequest request = airConditionerRequest(row); AirConditionerSpecification current = existing.get(request.getSpecCode());
                if (current == null) { airConditionerService.create(request); created++; } else { airConditionerService.update(current.getId(), request); updated++; }
            }
        } else {
            Map<String, BusinessDictionary> existing = dictionaryService.list(type, false).stream().collect(Collectors.toMap(BusinessDictionary::getItemValue, Function.identity()));
            for (RowValues row : rows) {
                BusinessDictionaryRequest request = dictionaryRequest(type, row); BusinessDictionary current = existing.get(request.getItemValue());
                if (current == null) { dictionaryService.create(request); created++; } else { dictionaryService.update(current.getId(), request); updated++; }
            }
        }
        return new DictionaryImportResult(rows.size(), created, updated);
    }

    private BusinessDictionaryRequest dictionaryRequest(String type, RowValues row) {
        BusinessDictionaryRequest request = new BusinessDictionaryRequest();
        request.setDictType(type); request.setItemValue(text(row, "取值编码*")); request.setItemLabel(text(row, "显示名称*"));
        request.setSortOrder(integer(row, "排序", 0)); request.setStatus(status(row)); request.setRemark(blankToNull(text(row, "备注")));
        return request;
    }

    private CableSpecificationRequest cableRequest(RowValues row) {
        CableSpecificationRequest request = new CableSpecificationRequest();
        request.setSpecCode(text(row, "规格编码*")); request.setCableModel(text(row, "电缆型号*")); request.setSpecification(text(row, "规格*"));
        request.setRatedCurrentA(positiveDecimal(row, "单根额定电流(A)*", true)); request.setRatedPowerKw(positiveDecimal(row, "单根承载功率(kW)*", true));
        request.setSortOrder(integer(row, "排序", 0)); request.setStatus(status(row)); request.setRemark(blankToNull(text(row, "备注")));
        return request;
    }

    private PowerSwitchSpecificationRequest switchRequest(RowValues row) {
        PowerSwitchSpecificationRequest request = new PowerSwitchSpecificationRequest();
        request.setSpecCode(text(row, "规格编码*")); request.setBrand(blankToNull(text(row, "品牌"))); request.setSwitchModel(text(row, "开关型号*"));
        request.setSwitchType(switchType(row)); request.setPoleCount(text(row, "极数*").toUpperCase(Locale.ROOT));
        request.setRatedCurrentA(positiveDecimal(row, "额定电流(A)*", true)); request.setBreakingCapacityKa(positiveDecimal(row, "分断能力(kA)", false));
        request.setSortOrder(integer(row, "排序", 0)); request.setStatus(status(row)); request.setRemark(blankToNull(text(row, "备注")));
        return request;
    }

    private AirConditionerSpecificationRequest airConditionerRequest(RowValues row) {
        AirConditionerSpecificationRequest request = new AirConditionerSpecificationRequest();
        request.setSpecCode(text(row, "规格编码*")); request.setModel(text(row, "空调型号*")); request.setEquipmentType(equipmentType(row));
        request.setRatedPowerKw(positiveDecimal(row, "额定功率(kW)*", true)); request.setAirflowCmh(integer(row, "额定出风量(m³/h)*", null));
        request.setSortOrder(integer(row, "排序", 0)); request.setStatus(status(row)); request.setRemark(blankToNull(text(row, "备注")));
        return request;
    }

    private String required(RowValues row, String header, int max) {
        String value = text(row, header);
        if (value.isBlank()) throw new IllegalArgumentException(header.replace("*", "") + "不能为空");
        if (value.length() > max) throw new IllegalArgumentException(header.replace("*", "") + "不能超过 " + max + " 个字符");
        return value;
    }

    private String optional(RowValues row, String header, int max) {
        String value = text(row, header);
        if (value.length() > max) throw new IllegalArgumentException(header + "不能超过 " + max + " 个字符");
        return value;
    }

    private Integer integer(RowValues row, String header, Integer defaultValue) {
        String value = text(row, header);
        if (value.isBlank()) return defaultValue;
        try {
            BigDecimal number = new BigDecimal(value.replace(",", ""));
            return number.intValueExact();
        } catch (Exception ex) {
            throw new IllegalArgumentException(header.replace("*", "") + "必须是整数");
        }
    }

    private int sortOrder(RowValues row) {
        Integer value = integer(row, "排序", 0);
        if (value < 0) throw new IllegalArgumentException("排序不能小于 0");
        return value;
    }

    private BigDecimal positiveDecimal(RowValues row, String header, boolean required) {
        String value = text(row, header);
        if (value.isBlank()) {
            if (required) throw new IllegalArgumentException(header.replace("*", "") + "不能为空");
            return null;
        }
        try {
            BigDecimal number = new BigDecimal(value.replace(",", ""));
            if (number.signum() <= 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(header.replace("*", "") + "必须是大于 0 的数字");
        }
    }

    private int status(RowValues row) {
        String value = text(row, "状态");
        if (value.isBlank() || "启用".equals(value) || "1".equals(value)) return 1;
        if ("停用".equals(value) || "0".equals(value)) return 0;
        throw new IllegalArgumentException("状态只能填写启用、停用、1 或 0");
    }

    private String switchType(RowValues row) {
        String value = text(row, "开关类型").trim();
        if (value.isBlank()) return null;
        Map<String, String> values = Map.of("微型断路器", "MCB", "塑壳断路器", "MCCB", "漏电断路器", "RCBO", "隔离开关", "ISOLATOR", "其他", "OTHER");
        String result = values.getOrDefault(value, value.toUpperCase(Locale.ROOT));
        if (!Set.of("MCB", "MCCB", "RCBO", "ISOLATOR", "OTHER").contains(result)) throw new IllegalArgumentException("开关类型不正确");
        return result;
    }

    private String equipmentType(RowValues row) {
        String value = text(row, "空调类型*").trim();
        Map<String, String> values = Map.of("风机盘管", "FAN_COIL", "吊柜式空调", "HANGING_CABINET", "其他", "OTHER");
        String result = values.getOrDefault(value, value.toUpperCase(Locale.ROOT));
        if (!Set.of("FAN_COIL", "HANGING_CABINET", "OTHER").contains(result)) throw new IllegalArgumentException("空调类型不正确");
        return result;
    }

    private String codeHeader(String type) { return Set.of(CABLE, SWITCH, AIR_CONDITIONER).contains(type) ? "规格编码*" : "取值编码*"; }
    private String text(RowValues row, String header) { return row.values().getOrDefault(header, "").trim(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String statusLabel(Integer status) { return Integer.valueOf(1).equals(status) ? "启用" : "停用"; }
    private String equipmentTypeLabel(String value) { return "FAN_COIL".equals(value) ? "风机盘管" : "HANGING_CABINET".equals(value) ? "吊柜式空调" : "其他"; }

    private String errorMessage(List<String> errors) {
        int shown = Math.min(errors.size(), 20);
        String message = String.join("；", errors.subList(0, shown));
        return errors.size() > shown ? message + "；另有 " + (errors.size() - shown) + " 个错误" : message;
    }

    private record Column(String header, int width) { }
    private record RowValues(int number, Map<String, String> values) { }
}
