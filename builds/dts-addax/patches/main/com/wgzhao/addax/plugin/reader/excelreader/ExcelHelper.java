/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one
 *  * or more contributor license agreements.  See the NOTICE file
 *  * distributed with this work for additional information
 *  * regarding copyright ownership.  The ASF licenses this file
 *  * to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance
 *  * with the License.  You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  * KIND, either express or implied.  See the License for the
 *  * specific language governing permissions and limitations
 *  * under the License.
 *
 */

package com.wgzhao.addax.plugin.reader.excelreader;

import static com.wgzhao.addax.core.spi.ErrorCode.IO_ERROR;
import static com.wgzhao.addax.core.spi.ErrorCode.ILLEGAL_VALUE;

import com.wgzhao.addax.core.element.BoolColumn;
import com.wgzhao.addax.core.element.DateColumn;
import com.wgzhao.addax.core.element.DoubleColumn;
import com.wgzhao.addax.core.element.LongColumn;
import com.wgzhao.addax.core.element.Record;
import com.wgzhao.addax.core.element.StringColumn;
import com.wgzhao.addax.core.exception.AddaxException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * DTS-maintained overlay for Addax 6.0.8 ExcelHelper.
 *
 * <p>Addax 6.0.8 iterates only physically present cells. Sparse XLSX rows therefore shift
 * values left and emit fewer columns than the header. Iterate by column index instead so
 * missing cells become explicit empty values. When {@code header=true}, the header defines
 * a fixed record width and wider rows fail immediately instead of expanding work or silently
 * dropping data. Without a header, positional gaps are preserved through each row's last
 * physical cell, but trailing empty columns cannot be inferred.
 */
public class ExcelHelper
{
    public boolean header;
    public int skipRows;
    FileInputStream file;
    Workbook workbook;
    private FormulaEvaluator evaluator;
    private Iterator<Row> rowIterator;
    private int expectedColumnCount;

    public ExcelHelper(boolean header, int skipRows)
    {
        this.header = header;
        this.skipRows = skipRows;
    }

    public void open(String filePath)
    {
        try {
            this.file = new FileInputStream(filePath);
            workbook = WorkbookFactory.create(file);
            // ONLY read the first sheet
            Sheet sheet = workbook.getSheetAt(0);
            Row widthReferenceRow = sheet.getRow(sheet.getFirstRowNum());
            this.expectedColumnCount =
                    widthReferenceRow == null ? 0 : Math.max(0, widthReferenceRow.getLastCellNum());
            this.evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            this.rowIterator = sheet.iterator();
            if (this.header && this.rowIterator.hasNext()) {
                // skip header
                this.rowIterator.next();
            }
            if (this.skipRows > 0) {
                int i = 0;
                while (this.rowIterator.hasNext() && i < this.skipRows) {
                    this.rowIterator.next();
                    i++;
                }
            }
        }
        catch (FileNotFoundException e) {
            throw AddaxException.asAddaxException(IO_ERROR, e);
        }
        catch (IOException e) {
            throw AddaxException.asAddaxException(
                    IO_ERROR, "IOException occurred when open '" + filePath + "':" + e.getMessage());
        }
    }

    public void close()
    {
        try {
            this.workbook.close();
            this.file.close();
        }
        catch (IOException ignored) {
        }
    }

    public Record readLine(Record record)
    {
        if (!rowIterator.hasNext()) {
            return null;
        }

        Row row = rowIterator.next();
        int rowColumnCount = Math.max(0, row.getLastCellNum());
        boolean hasHeaderSchema = header && expectedColumnCount > 0;
        if (hasHeaderSchema && rowColumnCount > expectedColumnCount) {
            throw AddaxException.asAddaxException(
                    ILLEGAL_VALUE,
                    "Excel row has "
                            + rowColumnCount
                            + " columns, exceeding the "
                            + expectedColumnCount
                            + "-column header schema.");
        }
        int columnCount = hasHeaderSchema ? expectedColumnCount : rowColumnCount;
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                record.addColumn(new StringColumn(""));
            }
            else {
                appendCell(record, cell);
            }
        }
        return record;
    }

    private void appendCell(Record record, Cell cell)
    {
        // Check the cell type after evaluating formulae.
        CellType cellType = evaluator.evaluateInCell(cell).getCellType();
        if (cellType == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                record.addColumn(new DateColumn(cell.getDateCellValue()));
            }
            else {
                double value = cell.getNumericCellValue();
                if ((long) value == value) {
                    record.addColumn(new LongColumn((long) value));
                }
                else {
                    record.addColumn(new DoubleColumn(value));
                }
            }
        }
        else if (cellType == CellType.STRING) {
            record.addColumn(new StringColumn(cell.getStringCellValue().trim()));
        }
        else if (cellType == CellType.BOOLEAN) {
            record.addColumn(new BoolColumn(cell.getBooleanCellValue()));
        }
        else if (cellType == CellType.ERROR) {
            record.addColumn(new StringColumn());
        }
        else if (cellType == CellType.BLANK) {
            record.addColumn(new StringColumn(""));
        }
    }
}
