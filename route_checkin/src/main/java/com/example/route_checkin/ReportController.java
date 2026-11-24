package com.example.route_checkin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {
    private final ProjectService service;

    public ReportController(ProjectService service) {
        this.service = service;
    }

    @GetMapping("/rankings.docx")
    public ResponseEntity<byte[]> rankingsDocx() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("城市路线打卡统计报表");
            addTable(document, "路线收藏排行", service.rankings("routes"), "routeId", "title");
            addTable(document, "用户积分排行", service.rankings("users"), "userId", "nickname");
            addTable(document, "地图点位欢迎度排行", service.rankings("points"), "pointId", "pointName");
            document.write(out);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=route-rankings.docx")
                .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(out.toByteArray());
        }
    }

    private void addTable(XWPFDocument document, String title, List<Map<String, Object>> rows,
                          String idKey, String nameKey) {
        document.createParagraph().createRun().setText(title);
        XWPFTable table = document.createTable();
        table.getRow(0).getCell(0).setText("编号");
        table.getRow(0).addNewTableCell().setText("名称");
        table.getRow(0).addNewTableCell().setText("统计值");
        for (Map<String, Object> row : rows) {
            var cells = table.createRow().getTableCells();
            cells.get(0).setText(String.valueOf(row.get(idKey)));
            cells.get(1).setText(String.valueOf(row.get(nameKey)));
            cells.get(2).setText(String.valueOf(row.get("score")));
        }
    }
}
