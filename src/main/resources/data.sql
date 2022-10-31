/*[create_table]*/
create table if not exists record
(
    id      integer not null
    constraint record_pk
    primary key autoincrement,
    name    text,
    version text,
    publish integer(1),
    dt      timestamp default current_timestamp
    );

create index if not exists record_dt_index
    on record (dt desc);

create index if not exists record_name_index
    on record (name);

create index if not exists record_publish_index
    on record (publish);

create index if not exists record_version_index
    on record (version desc);
;;

/*[all]*/
select id, name, version, publish from record where publish = 0;;

/*[get_by_name]*/
select id, name, version, publish from record where name like '%' || :name || '%' order by name,version desc;;