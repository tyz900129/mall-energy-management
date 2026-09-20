package com.wanyuecity.energy.system.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.BusinessDictionary;
import com.wanyuecity.energy.system.service.dto.BusinessDictionaryRequest;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryExcelServiceTest {
    @Mock private BusinessDictionaryService dictionaryService;
    @Mock private CableSpecificationService cableService;
    @Mock private PowerSwitchSpecificationService switchService;
    @Mock private AirConditionerSpecificationService airConditionerService;
    private DictionaryExcelService service;

    @BeforeEach
    void setUp() {
        service = new DictionaryExcelService(dictionaryService, cableService, switchService, airConditionerService);
    }

    @Test
    void createsCategorySpecificTemplate() throws Exception {
        byte[] bytes = service.export("CABLE", true);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("数据").getRow(0).getCell(0).getStringCellValue()).isEqualTo("规格编码*");
            assertThat(workbook.getSheet("数据").getRow(0).getCell(4).getStringCellValue()).isEqualTo("单根承载功率(kW)*");
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex("_meta"))).isTrue();
        }
    }

    @Test
    void importsStandardDictionaryByCreatingAndUpdatingCodes() throws Exception {
        BusinessDictionary existing = new BusinessDictionary();
        existing.setId(9L); existing.setItemValue("F1");
        when(dictionaryService.list("FLOOR", false)).thenReturn(List.of(existing));

        MockMultipartFile file = workbook("FLOOR", new String[][]{
            {"F1", "一层（更新）", "1", "启用", ""},
            {"F2", "二层", "2", "停用", "测试"}
        });
        var result = service.importWorkbook("FLOOR", file);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        ArgumentCaptor<BusinessDictionaryRequest> updated = ArgumentCaptor.forClass(BusinessDictionaryRequest.class);
        verify(dictionaryService).update(org.mockito.ArgumentMatchers.eq(9L), updated.capture());
        assertThat(updated.getValue().getItemLabel()).isEqualTo("一层（更新）");
        ArgumentCaptor<BusinessDictionaryRequest> created = ArgumentCaptor.forClass(BusinessDictionaryRequest.class);
        verify(dictionaryService).create(created.capture());
        assertThat(created.getValue().getItemValue()).isEqualTo("F2");
        assertThat(created.getValue().getStatus()).isZero();
    }

    @Test
    void rejectsWholeFileWhenAnyRowIsInvalid() throws Exception {
        MockMultipartFile file = workbook("FLOOR", new String[][]{{"F1", "", "0", "启用", ""}});
        assertThatThrownBy(() -> service.importWorkbook("FLOOR", file))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("第 2 行")
            .hasMessageContaining("显示名称不能为空");
        verify(dictionaryService, never()).create(org.mockito.ArgumentMatchers.any());
        verify(dictionaryService, never()).update(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    private MockMultipartFile workbook(String type, String[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("数据");
            var header = sheet.createRow(0);
            String[] headers = {"取值编码*", "显示名称*", "排序", "状态", "备注"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
            }
            workbook.createSheet("_meta").createRow(0).createCell(0).setCellValue(type);
            workbook.write(output);
            return new MockMultipartFile("file", "dictionary.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
