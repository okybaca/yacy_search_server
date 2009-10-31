// DCEntry.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.04.2009 on http://yacy.net
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


package net.yacy.document.content;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.text.Collator;
import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.TreeMap;

import net.yacy.document.Document;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.util.DateFormatter;


public class DCEntry extends TreeMap<String, String> {
    
    private static final long    serialVersionUID = -2050291583515701559L;
    
    // use a collator to relax when distinguishing between lowercase und uppercase letters
    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }
    public  static final DCEntry poison = new DCEntry();
    
    public DCEntry() {
        super((Collator) insensitiveCollator.clone());
    }
    
    public DCEntry(
            DigestURI url,
            Date date,
            String title,
            String author,
            String body
            ) {
        super((Collator) insensitiveCollator.clone());
        this.put("dc:identifier", url.toNormalform(true, false));
        this.put("dc:date", DateFormatter.formatISO8601(date));
        this.put("dc:title", title);
        this.put("dc:creator", author);
        this.put("dc:description", body);
    }
    
    /*
    DC according to rfc 5013

    * dc_title
    * dc_creator
    * dc_subject
    * dc_description
    * dc_publisher
    dc_contributor
    dc_date
    dc_type
    * dc_format
    * dc_identifier
    * dc_source
    dc_language
    dc_relation
    dc_coverage
    dc_rights
         */
    public Date getDate() {
        String d = this.get("docdatetime");
        if (d == null) d = this.get("dc:date");
        if (d == null) return null;
        try {
            return DateFormatter.parseISO8601(d);
        } catch (ParseException e) {
            e.printStackTrace();
            return new Date();
        }
    }
    
    public DigestURI getIdentifier() {
        String u = this.get("url");
        if (u == null) u = this.get("dc:identifier");
        if (u == null) return null;
        try {
            return new DigestURI(u, null);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public String getLanguage() {
        String l = this.get("language");
        if (l == null) l = this.get("dc:language");
        if (l == null) return getIdentifier().language();
        return l;
    }
    
    public String getType() {
        String t = this.get("dc:type");
        if (t == null) return "";
        return t;
    }
    
    public String getFormat() {
        String t = this.get("dc:format");
        if (t == null) return "";
        return t;
    }
    
    public String getSource() {
        String t = this.get("dc:source");
        if (t == null) return "";
        return t;
    }
    
    public String getRights() {
        String t = this.get("dc:rights");
        if (t == null) return "";
        return t;
    }
    
    public String getTitle() {
        String t = this.get("title");
        if (t == null) t = this.get("dc:title");
        t = stripCDATA(t);
        if (t == null) return "";
        return t;
    }
    
    public String getPublisher() {
        String t = this.get("dc:publisher");
        t = stripCDATA(t);
        if (t == null) return "";
        return t;
    }
    
    public String getCreator() {
        String t = this.get("author");
        if (t == null) t = this.get("dc:creator");
        t = stripCDATA(t);
        if (t == null) return "";
        return t;
    }
    
    public String getDescription() {
        String t = this.get("body");
        if (t == null) t = this.get("dc:description");
        t = stripCDATA(t);
        if (t == null) return "";
        return t;
    }
    
    public String[] getSubject() {
        String t = this.get("categories");
        if (t == null) this.get("dc:subject");
        t = stripCDATA(t);
        if (t == null) return new String[]{};
        return t.split(";");
    }
    
    private String stripCDATA(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("<![CDATA[")) s = s.substring(9);
        if (s.endsWith("]]")) s = s.substring(0, s.length() - 2);
        return s;
    }
    
    public Document document() {
        HashSet<String> languages = new HashSet<String>();
        languages.add(getLanguage());
        
        try {
            return new Document(
                getIdentifier(),
                "text/html",
                "UTF-8",
                languages,
                getSubject(),
                getTitle(),
                getCreator(),
                null,
                "",
                getDescription().getBytes("UTF-8"),
                null,
                null);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public void writeXML(OutputStreamWriter os) throws IOException {
        Document doc = document();
        if (doc != null) {
            doc.writeXML(os, this.getDate());
        }
    }
}