package com.bighealth.client;

public class SchemasApiClient extends ApiClient {

    public SchemasApiClient() {
        super(SCHEMAS_API_URL);
    }

    public void execute() throws Exception {
        super.execute();
    }

    public static void main(String[] args) {
        SchemasApiClient client = new SchemasApiClient();
        try {
            client.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
