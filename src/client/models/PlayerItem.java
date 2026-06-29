package client.models;

/**
 * Модель игрока для отображения в списке лобби.
 *
 * Содержит информацию об игроке:
 * - Уникальный ID
 * - Отображаемое имя
 */
public class PlayerItem {
    /** Уникальный идентификатор игрока на сервере */
    public final int id;

    /** Отображаемое имя игрока */
    public final String name;


    public PlayerItem(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return name + " (ID: " + id + ")";
    }
}