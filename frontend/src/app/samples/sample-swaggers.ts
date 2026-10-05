export const HOST_SYSTEM_A_SWAGGER = JSON.stringify({
  "openapi": "3.0.3",
  "info": {
    "title": "System A - Legacy Core POS & Billing API",
    "version": "1.0.0",
    "description": "Host System API for legacy point-of-sale and customer billing. Uses snake_case naming conventions, DD/MM/YYYY date formats, and string status flags."
  },
  "servers": [
    {
      "url": "https://pos.internal.enterprise.org/api/v1",
      "description": "Internal Core POS Gateway"
    }
  ],
  "paths": {
    "/pos/orders": {
      "post": {
        "summary": "Submit Order / Customer Record",
        "description": "Submits a point-of-sale customer order from the legacy terminal",
        "operationId": "submitLegacyOrder",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": {
                "$ref": "#/components/schemas/LegacyOrderPayload"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Order acknowledged",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyOrderPayload"
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "schemas": {
      "LegacyOrderPayload": {
        "type": "object",
        "required": [
          "order_num",
          "cust_id",
          "name",
          "total_amount"
        ],
        "properties": {
          "order_num": {
            "type": "string",
            "description": "Legacy unique order sequence identifier",
            "example": "ORD-2026-991"
          },
          "cust_id": {
            "type": "string",
            "description": "Customer alphanumeric identifier in legacy POS",
            "example": "CUST-4081"
          },
          "name": {
            "type": "string",
            "description": "Customer full legal name",
            "example": "Dr. Sarah Connor"
          },
          "dob": {
            "type": "string",
            "format": "dd/MM/yyyy",
            "description": "Date of birth in legacy UK/Indian format DD/MM/YYYY",
            "example": "15/08/1988"
          },
          "active": {
            "type": "string",
            "enum": [
              "Yes",
              "No"
            ],
            "description": "Account active flag ('Yes' / 'No')",
            "example": "Yes"
          },
          "tier": {
            "type": "string",
            "enum": [
              "STANDARD_TIER",
              "SILVER_TIER",
              "GOLD_TIER",
              "PLATINUM_TIER"
            ],
            "description": "Customer loyalty tier identifier",
            "example": "GOLD_TIER"
          },
          "contact": {
            "type": "object",
            "description": "Nested contact communications object",
            "properties": {
              "email": {
                "type": "string",
                "format": "email",
                "description": "Primary customer contact email",
                "example": "sarah.c@cyberdyne.org"
              },
              "phone": {
                "type": "string",
                "format": "phone",
                "description": "Primary telephone contact number",
                "example": "+1-555-0199"
              }
            }
          },
          "total_amount": {
            "type": "number",
            "format": "double",
            "description": "Total gross purchase amount in USD",
            "example": 499.99
          }
        }
      }
    }
  }
}, null, 2);

export const DESTINATION_SYSTEM_B_SWAGGER = JSON.stringify({
  "openapi": "3.0.3",
  "info": {
    "title": "System B - Modern Cloud ERP & CRM API",
    "version": "2.0.0",
    "description": "Destination Cloud ERP & Salesforce Integration API. Uses camelCase naming conventions, ISO-8601 YYYY-MM-DD dates, native booleans, and 3-letter tier codes."
  },
  "servers": [
    {
      "url": "https://api.clouderp.enterprise.com/v2",
      "description": "Cloud ERP Production Ingestion Gateway"
    }
  ],
  "paths": {
    "/erp/orders": {
      "post": {
        "summary": "Ingest Enterprise Order Record",
        "description": "Ingests customer order transaction into Cloud ERP system of record",
        "operationId": "createCloudOrder",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": {
                "$ref": "#/components/schemas/CloudErpOrderRecord"
              }
            }
          }
        },
        "responses": {
          "201": {
            "description": "Created order in Cloud ERP",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/CloudErpOrderRecord"
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "schemas": {
      "CloudErpOrderRecord": {
        "type": "object",
        "required": [
          "orderId",
          "customerId",
          "userName",
          "totalAmount"
        ],
        "properties": {
          "orderId": {
            "type": "string",
            "description": "Canonical enterprise order sequence identifier",
            "example": "ORD-2026-991"
          },
          "customerId": {
            "type": "string",
            "description": "Unified master customer identifier in Cloud ERP",
            "example": "CUST-4081"
          },
          "userName": {
            "type": "string",
            "description": "Customer display username / full name",
            "example": "Dr. Sarah Connor"
          },
          "dateOfBirth": {
            "type": "string",
            "format": "yyyy-MM-dd",
            "description": "Date of birth in standard ISO-8601 format (YYYY-MM-DD)",
            "example": "1988-08-15"
          },
          "accountEnabled": {
            "type": "boolean",
            "description": "Active status boolean flag",
            "example": true
          },
          "membershipLevel": {
            "type": "string",
            "enum": [
              "STD",
              "SLV",
              "GLD",
              "PLT"
            ],
            "description": "Consolidated 3-letter customer tier membership code",
            "example": "GLD"
          },
          "emailAddress": {
            "type": "string",
            "format": "email",
            "description": "Verified customer direct email address",
            "example": "sarah.c@cyberdyne.org"
          },
          "telephone": {
            "type": "string",
            "format": "phone",
            "description": "Customer direct telephone contact number",
            "example": "+1-555-0199"
          },
          "totalAmount": {
            "type": "number",
            "format": "double",
            "description": "Final ledger total transaction monetary amount in USD",
            "example": 499.99
          }
        }
      }
    }
  }
}, null, 2);
