package com.company.officecommute.domain.overtime;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "response")
public class HolidayResponse {
    @JacksonXmlProperty(localName = "body")
    private Body body;

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    public static class Body {
        @JacksonXmlElementWrapper(localName = "items")
        @JacksonXmlProperty(localName = "item")
        private List<Item> items;

        public List<Item> getItems() {
            return items;
        }

        public void setItems(List<Item> items) {
            this.items = items;
        }
    }

    public static class Item {
        @JacksonXmlProperty(localName = "isHoliday")
        private String isHoliday;

        public String getIsHoliday() {
            return isHoliday;
        }

        public void setIsHoliday(String isHoliday) {
            this.isHoliday = isHoliday;
        }
    }

}
