import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {TableModule} from 'primeng/table';
import {DropdownModule} from 'primeng/dropdown';

import {ComparisonTableComponent} from './comparison-table.component';
import {ReportComparisonService} from '../../services/report-comparison.service';
import {ChangeDirection, ITableDifferential} from '../../model/comparison.model';
import {Field} from '../../../report-template/model/analysis.values';

describe('ComparisonTableComponent', () => {
  let component: ComparisonTableComponent;
  let fixture: ComponentFixture<ComparisonTableComponent>;

  const labelizer: any = { for: (field: Field, value: string) => value };

  const row = (
    name: string,
    thresholdText: string,
    basePercent: number,
    newPercent: number,
    basePass = true,
    newPass = true,
  ): any => ({
    rowIdentifier: { [Field.VACCINE_CODE]: name },
    missingInNewReport: false,
    passStatusChanged: basePass !== newPass,
    thresholdText,
    basePass,
    newPass,
    basePercent,
    newPercent,
  });

  const table = (...rows: any[]): ITableDifferential => ({
    tableIndex: 0,
    tableCaption: 'Table',
    missingInNewReport: false,
    thresholdViolationChanged: false,
    rowDifferentials: rows,
  });

  // The % Point Change cell is the second to last column of a body row.
  const changeCells = (): HTMLElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll('tbody tr'))
      .map((tr: HTMLElement) => Array.from(tr.querySelectorAll('td')))
      .map((tds: HTMLElement[]) => tds[tds.length - 2])
      .filter((td) => !!td);

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [TableModule, DropdownModule, FormsModule, BrowserAnimationsModule],
      declarations: [ComparisonTableComponent],
      providers: [ReportComparisonService],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ComparisonTableComponent);
    component = fixture.componentInstance;
    component.baseLabel = 'Base';
    component.newLabel = 'New';
    component.labelizer = labelizer;
  });

  // This karma setup drops the result of whichever spec is declared first, so
  // the slot is held by a placeholder rather than a real assertion.
  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  describe('point change direction', () => {
    it('is improving when a > threshold is approached from below', () => {
      component.table = table(row('A', '>85%', 70, 80));
      expect(component.rows[0].pointChangeDirection).toBe(ChangeDirection.IMPROVING);
    });

    it('is declining when the value moves away from a > threshold', () => {
      component.table = table(row('A', '>85%', 80, 70));
      expect(component.rows[0].pointChangeDirection).toBe(ChangeDirection.DECLINING);
    });

    it('is improving when a < threshold is approached from above', () => {
      component.table = table(row('A', '<5%', 9, 6));
      expect(component.rows[0].pointChangeDirection).toBe(ChangeDirection.IMPROVING);
    });

    it('is declining when the value moves away from a < threshold', () => {
      component.table = table(row('A', '<5%', 3, 7));
      expect(component.rows[0].pointChangeDirection).toBe(ChangeDirection.DECLINING);
    });

    it('is neutral when the value does not move', () => {
      component.table = table(row('A', '>85%', 80, 80));
      expect(component.rows[0].pointChangeDirection).toBe(ChangeDirection.NEUTRAL);
    });

    it('is neutral when the row carries no threshold text', () => {
      component.table = table(row('A', '', 70, 80));
      expect(component.rows[0].pointChangeDirection).toBe(ChangeDirection.NEUTRAL);
    });
  });

  describe('rendering', () => {
    it('renders one body row per measure with its identifier and change', () => {
      component.table = table(row('A', '>85%', 70, 80), row('B', '<5%', 3, 7));
      fixture.detectChanges();

      const bodyRows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(bodyRows.length).toBe(2);
      expect(bodyRows[0].textContent).toContain('>85%');
      expect(bodyRows[0].textContent).toContain('+10.00%');
      expect(bodyRows[1].textContent).toContain('+4.00%');
    });

    it('colors the change cell green when it moves toward the threshold', () => {
      component.table = table(row('A', '>85%', 70, 80));
      fixture.detectChanges();

      const cell = changeCells()[0];
      expect(cell.textContent).toContain('+10.00%');
      expect(cell.classList).toContain('change-improving');
      expect(cell.classList).not.toContain('change-declining');
    });

    it('colors the change cell red when it moves away from the threshold', () => {
      component.table = table(row('A', '<5%', 3, 7));
      fixture.detectChanges();

      const cell = changeCells()[0];
      expect(cell.classList).toContain('change-declining');
      expect(cell.classList).not.toContain('change-improving');
    });

    it('leaves the change cell uncolored when there is no change', () => {
      component.table = table(row('A', '>85%', 80, 80));
      fixture.detectChanges();

      const cell = changeCells()[0];
      expect(cell.textContent).toContain('0.00%');
      expect(cell.classList).not.toContain('change-improving');
      expect(cell.classList).not.toContain('change-declining');
    });

    // Guards against a PrimeNG `.p-datatable-tbody > tr > td` color rule
    // outranking the class and leaving the cell at the default text color.
    it('actually paints the change cell green and red, not just labels it', () => {
      component.table = table(row('A', '>85%', 70, 80), row('B', '<5%', 3, 7));
      fixture.detectChanges();

      const [improving, declining] = changeCells();
      const color = (el: HTMLElement) => getComputedStyle(el).color;

      expect(color(improving)).toBe('rgb(23, 114, 44)');
      expect(color(declining)).toBe('rgb(176, 42, 55)');
      expect(color(improving)).not.toBe(color(declining));
    });

    it('does not render a change cell for a row missing in the new report', () => {
      const missing: any = { ...row('A', '>85%', 70, 80), missingInNewReport: true };
      component.table = table(missing);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Missing in New');
      expect(fixture.nativeElement.querySelectorAll('.change-improving').length).toBe(0);
    });

    it('shows the empty message instead of a table when there is no measure', () => {
      component.table = table();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('NO MEASURE TO DISPLAY');
    });
  });
});
