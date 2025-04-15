package com.bighealth.client;

public class DataApiClient extends ApiClient {

    public DataApiClient() {
        super(API_URL);
    }

    public void execute() throws Exception {
        super.execute();
    }

    public static void main(String[] args) {
        DataApiClient client = new DataApiClient();
        try {
            client.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
