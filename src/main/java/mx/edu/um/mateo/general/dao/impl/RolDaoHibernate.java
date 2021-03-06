/*
 * The MIT License
 *
 * Copyright 2012 Universidad de Montemorelos A. C.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package mx.edu.um.mateo.general.dao.impl;

import mx.edu.um.mateo.general.dao.BaseDao;
import mx.edu.um.mateo.general.dao.RolDao;
import mx.edu.um.mateo.general.model.Rol;
import org.hibernate.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author J. David Mendoza <jdmendoza@um.edu.mx>
 */
@Repository
@Transactional
public class RolDaoHibernate extends BaseDao implements RolDao {

    public RolDaoHibernate() {
        log.info("Nueva instancia de RolDao");
    }

    @Override
    @Transactional(readOnly = true)
    public Rol obtiene(Long id) {
        Rol rol = (Rol) currentSession().get(Rol.class, id);
        return rol;
    }

    @Override
    @Transactional(readOnly = true)
    public Rol obtiene(String nombre) {
        Query query = currentSession().createQuery(
                "select r from Rol r where authority =:Rol");
        query.setString("Rol", nombre);
        Rol rol = (Rol) query.uniqueResult();
        return rol;
    }

    @Override
    public Rol crea(Rol rol) {
        currentSession().save(rol);
        return rol;
    }
}
