CREATE TABLE account (
    id         bigint NOT NULL PRIMARY KEY,
    login      text NOT NULL,
    password   text NOT NULL,
    permission text NOT NULL,
    token      text NOT NULL
);