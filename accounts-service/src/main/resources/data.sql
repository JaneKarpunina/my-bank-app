INSERT INTO bank_accounts (username, name, birth_date, balance)
VALUES
    ('ivan_petrov', 'Иван Петров', '1995-05-15', 150000),
    ('anna_smirnova', 'Анна Смирнова', '1998-11-23', 45000),
    ('ivanov', 'Сергей Иванов', '1990-02-02', 8900)
ON CONFLICT (username) DO NOTHING;