package edu.cit.nunez.channel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "tiangge_cursor")
@SuppressWarnings("unused")
public class TianggeCursor {

    @Id
    private String id;
    private long cursorValue;

    public TianggeCursor() {}

    public TianggeCursor(String id, long cursorValue) {
        this.id = id;
        this.cursorValue = cursorValue;
    }

    public String getId() { return id; }
    public long getCursorValue() { return cursorValue; }
    public void setCursorValue(long cursorValue) { this.cursorValue = cursorValue; }
}

@SuppressWarnings("null")
interface TianggeCursorRepository extends JpaRepository<TianggeCursor, String> {}