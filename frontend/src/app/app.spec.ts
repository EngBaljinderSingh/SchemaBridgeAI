import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { HOST_SYSTEM_A_SWAGGER, DESTINATION_SYSTEM_B_SWAGGER } from './samples/sample-swaggers';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should load Host Swagger sample into sourceSchemaInput', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.loadHostSwaggerSample();
    expect(app.sourceSchemaType).toBe('OPENAPI');
    expect(app.sourceSchemaInput).toBe(HOST_SYSTEM_A_SWAGGER);
    expect(app.sourceSchemaInput).toContain('LegacyOrderPayload');
    expect(app.sourceSchemaInput).toContain('order_num');
  });

  it('should load Destination Swagger sample into targetSchemaInput', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.loadDestinationSwaggerSample();
    expect(app.targetSchemaType).toBe('OPENAPI');
    expect(app.targetSchemaInput).toBe(DESTINATION_SYSTEM_B_SWAGGER);
    expect(app.targetSchemaInput).toContain('CloudErpOrderRecord');
    expect(app.targetSchemaInput).toContain('orderId');
  });
});
