package com.example.route_checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class ReportControllerTest {
    @Test
    void exportsRankingsAsReadableWordDocument() throws Exception {
        ProjectService service = mock(ProjectService.class);
        when(service.rankings("routes")).thenReturn(
            List.of(Map.of("routeId", 7L, "title", "城市公园散步", "score", 12L)));
        when(service.rankings("users")).thenReturn(List.of());
        when(service.rankings("points")).thenReturn(List.of());

        byte[] bytes = new ReportController(service).rankingsDocx().getBody();
        assertNotNull(bytes);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertEquals("城市路线打卡统计报表", document.getParagraphs().get(0).getText());
            assertEquals("城市公园散步", document.getTables().get(0).getRow(1).getCell(1).getText());
            assertEquals("12", document.getTables().get(0).getRow(1).getCell(2).getText());
        }
    }
}
